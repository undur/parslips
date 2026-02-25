/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2005 The ObjectStyle Group,
 * and individual authors of the software.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        ObjectStyle Group (http://objectstyle.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "ObjectStyle Group" and "Cayenne"
 *    must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact andrus@objectstyle.org.
 *
 * 5. Products derived from this software may not be called "ObjectStyle"
 *    nor may "ObjectStyle" appear in their names without prior written
 *    permission of the ObjectStyle Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE OBJECTSTYLE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the ObjectStyle Group.  For more
 * information on the ObjectStyle Group, please see
 * <http://objectstyle.org/>.
 *
 */

package org.objectstyle.wolips.wodclipse.core.woo;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// FIXME: eomodeler removed — DisplayGroup is now a minimal stub.
// All EOEntity, EODataSource, EOSortOrdering, EOModelMap, etc. dependencies have been removed.
// WODisplayGroup / ERXDisplayGroup support is disabled.
// If this functionality is needed in the future, it should be reimplemented without eomodeler.

public class DisplayGroup {

  public static final String NAME = "name";
  public static final String CLASS_NAME = "className";
  public static final String CLASS_NAME_INDEX = "classNameIndex";
  public static final String CLASS_NAME_LIST = "classNameList";
  public static final String QUALIFICATION_INDEX = "qualificationIndex";
  public static final String QUALIFICATION_LIST = "qualificationList";
  public static final String ENTITY_NAME = "entityName";
  public static final String ENTITY_LIST = "entityList";
  public static final String MASTER_ENTITY_NAME = "masterEntityName";
  public static final String DETAIL_KEY_NAME = "detailKeyName";
  public static final String DETAIL_KEY_LIST = "detailKeyList";
  public static final String SORT_LIST = "sortList";
  public static final String HAS_MASTER_DETAIL = "hasMasterDetail";
  public static final String SORT_ORDER = "sortOrder";
  public static final String SORT_ORDER_KEY = "sortOrderKey";
  public static final String FETCH_SPEC_LIST = "fetchSpecList";
  public static final String FETCH_SPEC_NAME = "fetchSpecName";
  public static final String ENTRIES_PER_BATCH = "entriesPerBatch";
  public static final String FETCHES_ON_LOAD = "fetchesOnLoad";
  public static final String SELECTS_FIRST_OBJECT = "selectsFirstObject";
  public static final String EDITING_CONTEXT = "editingContext";

  public static final String ASCENDING = "Ascending";
  public static final String DESCENDING = "Descending";
  public static final String NOT_SORTED = "Not Sorted";
  public static final String FETCH_SPEC_NONE = "<None>";
  public static final String[] SORT_OPTIONS = new String[] { ASCENDING, DESCENDING, NOT_SORTED, };

  private String _originalName;
  private String _name = "newDisplayGroup";
  private WooModel _wooModel;
  private String _entityName;
  private boolean _hasMasterDetail;
  private String _className;
  private String _editingContext;
  private PropertyChangeSupport _changeSupport;

  public DisplayGroup(WooModel model) {
    _wooModel = model;
    _className = "WODisplayGroup";
    _hasMasterDetail = false;
    _changeSupport = new PropertyChangeSupport(this);
  }

  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    _changeSupport.addPropertyChangeListener(listener);
  }

  public void addPropertyChangeListener(final String name, final PropertyChangeListener listener) {
    _changeSupport.addPropertyChangeListener(name, listener);
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    _changeSupport.removePropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(final String name, final PropertyChangeListener listener) {
    _changeSupport.removePropertyChangeListener(name, listener);
  }

  protected void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    if (oldValue != newValue || (oldValue != null && !oldValue.equals(newValue)) || (newValue != null && !newValue.equals(oldValue))) {
      _wooModel.markAsDirty();
      _changeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  public String getClassName() {
    return _className;
  }

  public void setClassName(final String className) {
    String oldClass = _className;
    _className = className != null ? className : "WODisplayGroup";
    firePropertyChange(CLASS_NAME, oldClass, _className);
  }

  public String getEntityName() {
    return _entityName;
  }

  public void setEntityName(final String entity) {
    String oldEntityName = _entityName;
    _entityName = entity;
    firePropertyChange(ENTITY_NAME, oldEntityName, _entityName);
  }

  public String getEditingContext() {
    return _editingContext;
  }

  public String getName() {
    return _name;
  }

  public String getOriginalName() {
    return _originalName;
  }

  public void setName(final String name) {
    String oldName = _name;
    _name = name;
    firePropertyChange(NAME, oldName, _name);
  }

  public boolean isHasMasterDetail() {
    return _hasMasterDetail;
  }

  public WooModel getWooModel() {
    return _wooModel;
  }

  public void setWooModel(final WooModel model) {
    _wooModel = model;
  }

  // FIXME: eomodeler removed — loadFromMap() previously loaded display group config from EOModelMap
  // public void loadFromMap(final EOModelMap map, final Set<EOModelVerificationFailure> failures) { ... }

  // FIXME: eomodeler removed — toMap() previously serialized display group config to EOModelMap
  // public EOModelMap toMap() { ... }
}
