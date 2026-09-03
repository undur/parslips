#!/usr/bin/env python3
"""Turns the public changelog (wolips.p2/src/main/resources/changelog.html) into GitHub releases.

The changelog's dated cards ARE the project's release history; this reads them and renders
each as Markdown release notes, so a release is exactly "a changelog card with a version on
it". Used both for the standing rule (every new card gets a release) and for the backfill.

	changelog_release.py list                 # the cards: date, headline, entry count
	changelog_release.py notes "August 29, 2026"   # Markdown notes for one card (stdout)
	changelog_release.py title "August 29, 2026"   # the card's headline (first entry)
	changelog_release.py release 5.7.0        # THE RULE: publish the newest card as release v5.7.0

The `release` step is the standing rule "a public changelog card is a release": it takes the
newest card, checks the bundle already carries that version (bump first with
`mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=X.Y.Z-SNAPSHOT -Dtycho.mode=maven`,
so the number on GitHub, in Eclipse's About dialog and on the p2 site agree), tags HEAD as
vX.Y.Z, pushes the tag and creates the GitHub release named after the card's headline with the
card as notes. Numbering: minor bump for a feature card, patch bump for a fix-only card.
"""
import subprocess
import html
import re
import sys
from pathlib import Path

CHANGELOG = Path(__file__).resolve().parent.parent / "wolips.p2/src/main/resources/changelog.html"


def cards(source=None):
	"""[(date, [(title, body_html)])] in page order (newest first)."""
	text = source if source is not None else CHANGELOG.read_text()
	result = []
	for m in re.finditer(r'<h2 class="date-header">([^<]+)</h2>(.*?)(?=<h2 class="date-header">|<div class="footer">)', text, re.S):
		date = html.unescape(m.group(1)).strip()
		entries = []
		for e in re.finditer(r'<div class="changelog-entry">\s*<h3>(.*?)</h3>(.*?)</div>\s*(?=<div class="changelog-entry">|</div>)', m.group(2), re.S):
			entries.append((inline(e.group(1)).strip(), e.group(2)))
		result.append((date, entries))
	return result


def inline(fragment):
	"""Inline HTML → Markdown: code, strong, em, links, entities."""
	s = fragment
	s = re.sub(r'<code>(.*?)</code>', lambda m: '`' + html.unescape(m.group(1)) + '`', s, flags=re.S)
	s = re.sub(r'<strong>(.*?)</strong>', r'**\1**', s, flags=re.S)
	s = re.sub(r'<em>(.*?)</em>', r'*\1*', s, flags=re.S)
	s = re.sub(r'<a href="([^"]+)"[^>]*>(.*?)</a>', r'[\2](\1)', s, flags=re.S)
	s = re.sub(r'<br\s*/?>', '\n', s)
	s = re.sub(r'<[^>]+>', '', s)
	s = html.unescape(s)
	return re.sub(r'[ \t]*\n[ \t]*', ' ', s).strip()


def block(body_html):
	"""Block-level HTML of one entry → Markdown paragraphs and lists."""
	out = []
	for m in re.finditer(r'<p>(.*?)</p>|<ul>(.*?)</ul>|<pre>(.*?)</pre>', body_html, re.S):
		if m.group(1) is not None:
			out.append(inline(m.group(1)))
		elif m.group(2) is not None:
			items = re.findall(r'<li>(.*?)</li>', m.group(2), re.S)
			out.append('\n'.join('- ' + inline(i) for i in items))
		else:
			out.append('```\n' + html.unescape(re.sub(r'<[^>]+>', '', m.group(3))).strip() + '\n```')
	return '\n\n'.join(out)


def notes(card):
	date, entries = card
	parts = []
	for title, body in entries:
		parts.append('### ' + title + '\n\n' + block(body))
	return '\n\n'.join(parts) + '\n\n_From the [changelog](https://undur.github.io/parslips/repository/changelog.html), ' + date + '._\n'


def find(date):
	for card in cards():
		if card[0] == date:
			return card
	sys.exit('no changelog card dated ' + date)


MANIFEST = Path(__file__).resolve().parent.parent / "wolips/plugins/ng.componenteditor/META-INF/MANIFEST.MF"


def release(version):
	"""Publishes the newest changelog card as GitHub release v<version> from HEAD."""
	tag = 'v' + version
	bundle = re.search(r'^Bundle-Version:\s*(\S+)', MANIFEST.read_text(), re.M).group(1)
	if bundle != version + '.qualifier':
		sys.exit(f'bundle is {bundle}, not {version}.qualifier — bump it first (tycho-versions:set-version) so the release and the installed plugin agree')
	if subprocess.run(['git', 'status', '--porcelain'], capture_output=True, text=True).stdout.strip():
		sys.exit('working tree not clean — commit the changelog card (and the version bump) first')
	if tag in subprocess.run(['git', 'tag'], capture_output=True, text=True).stdout.split():
		sys.exit(tag + ' already exists')
	card = cards()[0]
	date, entries = card
	title = entries[0][0]
	notes_file = Path('/tmp') / (tag + '.md')
	notes_file.write_text(notes(card))
	subprocess.run(['git', 'tag', '-a', tag, '-m', f'Parsley Template Editor {version} (changelog: {date})'], check=True)
	subprocess.run(['git', 'push', 'origin', tag], check=True)
	subprocess.run(['gh', 'release', 'create', tag, '--title', title, '--notes-file', str(notes_file)], check=True)
	print(f'released {tag} — "{title}" ({date})')


if __name__ == '__main__':
	cmd = sys.argv[1] if len(sys.argv) > 1 else 'list'
	if cmd == 'list':
		for date, entries in cards():
			print(f'{date:<20} {len(entries):>2} entr{"y" if len(entries)==1 else "ies"}  {entries[0][0] if entries else "?"}')
	elif cmd == 'notes':
		print(notes(find(sys.argv[2])), end='')
	elif cmd == 'title':
		print(find(sys.argv[2])[1][0][0])
	elif cmd == 'release':
		release(sys.argv[2])
	else:
		sys.exit(__doc__)
