/*******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2012 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.trans.steps.detectrowchange;

import java.util.List;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;

/**
 * @author
 * 
 */
@Step( id = "detectrowchange", image = "DetectRowChange.png", name = "Detect change in row",
    description = "Returns rows when the specified fields change", categoryDescription = "Transform" )
public class DetectRowChangeMeta extends BaseStepMeta implements StepMetaInterface {
  private static Class<?> PKG = DetectRowChangeMeta.class; // for i18n purposes, needed by Translator2!! $NON-NLS-1$

  /** order by which fields? */
  private String fieldNames[];

  /** false : case insensitive, true=case sensitive */
  private boolean caseSensitive[];

  /** false : don't add oldValue field, true=add oldValue field */
  private boolean includeOldValue[];
  
  private long numRowsSinceLastChange = 0;

  public DetectRowChangeMeta() {
    super(); // allocate BaseStepMeta
  }

  public void allocate( int nrfields ) {
    fieldNames = new String[nrfields]; // order in which to detect changes
    caseSensitive = new boolean[nrfields];
    includeOldValue = new boolean[nrfields];
  }

  @Override
  public Object clone() {
    DetectRowChangeMeta retval = (DetectRowChangeMeta) super.clone();
    int nrfields = fieldNames.length;

    retval.allocate( nrfields );

    for ( int i = 0; i < nrfields; i++ ) {
      retval.fieldNames[i] = fieldNames[i];
      retval.caseSensitive[i] = caseSensitive[i];
      retval.includeOldValue[i] = includeOldValue[i];
    }
    return retval;
  }

  @Override
  public String getXML() throws KettleException {
    StringBuffer retval = new StringBuffer( 256 );

    retval.append( "    <fields>" ).append( Const.CR );
    for ( int i = 0; i < fieldNames.length; i++ ) {
      retval.append( "      <field>" ).append( Const.CR );
      retval.append( "        " ).append( XMLHandler.addTagValue( "name", fieldNames[i] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "case_sensitive", caseSensitive[i] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "include_old_value", includeOldValue[i] ) );
      retval.append( "      </field>" ).append( Const.CR );
    }
    retval.append( "    </fields>" ).append( Const.CR );

    return retval.toString();
  }

  @Override
  public void loadXML( Node stepnode, List<DatabaseMeta> databases, IMetaStore metaStore ) throws KettleXMLException {
    readData( stepnode );
  }

  private void readData( Node stepnode ) throws KettleXMLException {
    try {
      Node fields = XMLHandler.getSubNode( stepnode, "fields" );
      int nrfields = XMLHandler.countNodes( fields, "field" );

      allocate( nrfields );

      for ( int i = 0; i < nrfields; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( fields, "field", i );

        fieldNames[i] = XMLHandler.getTagValue( fnode, "name" );
        String sens = XMLHandler.getTagValue( fnode, "case_sensitive" );
        caseSensitive[i] = Const.isEmpty( sens ) || "Y".equalsIgnoreCase( sens );
        String keepOld = XMLHandler.getTagValue( fnode, "include_old_value" );
        includeOldValue[i] = Const.isEmpty( keepOld ) || "Y".equalsIgnoreCase( keepOld );
      }
    } catch ( Exception e ) {
      throw new KettleXMLException( "Unable to load step info from XML", e );
    }
  }

  @Override
  public void setDefault() {
    int nrfields = 0;

    allocate( nrfields );

    for ( int i = 0; i < nrfields; i++ ) {
      fieldNames[i] = "field" + i;
      caseSensitive[i] = true;
      includeOldValue[i] = false;
    }
  }

  @Override
  public void readRep( Repository rep, IMetaStore metaStore, ObjectId id_step, List<DatabaseMeta> databases )
    throws KettleException {
  }

  @Override
  public void saveRep( Repository rep, IMetaStore metaStore, ObjectId id_transformation, ObjectId id_step )
    throws KettleException {
  }

  @Override
  public void getFields( RowMetaInterface rowMeta, String origin, RowMetaInterface[] info, StepMeta nextStep,
      VariableSpace space, Repository repository, IMetaStore metaStore ) throws KettleStepException {
    // Default: nothing changes to rowMeta
  }

  @Override
  public void check( List<CheckResultInterface> remarks, TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev,
      String input[], String output[], RowMetaInterface info, VariableSpace space, Repository repository,
      IMetaStore metaStore ) {
    CheckResult cr;
    if ( prev == null || prev.size() == 0 ) {
      cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_WARNING, BaseMessages.getString( PKG,
              "DetectRowChangeMeta.CheckResult.NotReceivingFields" ), stepMeta );
      remarks.add( cr );
    } else {
      cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString( PKG,
              "DetectRowChangeMeta.CheckResult.StepRecevingData", prev.size() + "" ), stepMeta );
      remarks.add( cr );
    }

    // See if we have input streams leading to this step!
    if ( input.length > 0 ) {
      cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString( PKG,
              "DetectRowChangeMeta.CheckResult.StepRecevingData2" ), stepMeta );
      remarks.add( cr );
    } else {
      cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString( PKG,
              "DetectRowChangeMeta.CheckResult.NoInputReceivedFromOtherSteps" ), stepMeta );
      remarks.add( cr );
    }
  }

  @Override
  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta tr,
      Trans trans ) {
    return new DetectRowChange( stepMeta, stepDataInterface, cnr, tr, trans );
  }

  @Override
  public StepDataInterface getStepData() {
    return new DetectRowChangeData();
  }

  public String[] getFieldNames() {
    return fieldNames;
  }

  public void setFieldNames( String[] fieldNames ) {
    this.fieldNames = fieldNames;
  }

  public boolean[] getCaseSensitive() {
    return caseSensitive;
  }

  public void setCaseSensitive( boolean[] caseSensitive ) {
    this.caseSensitive = caseSensitive;
  }

  public boolean[] getIncludeOldValue() {
    return includeOldValue;
  }

  public void setIncludeOldValue( boolean[] includeOldValue ) {
    this.includeOldValue = includeOldValue;
  }
  
  public int findNumTrackedValues() {
    int numTrackedValues = 0;
    
    for(int i=0; i<includeOldValue.length; i++) {
      if(includeOldValue[i]) {
        numTrackedValues++;
      }
    }
    return numTrackedValues;
  }
}
