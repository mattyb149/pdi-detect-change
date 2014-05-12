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

import java.util.ArrayList;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaBoolean;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.core.row.value.ValueMetaInteger;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;

/**
 * The Detect Row Change step will output a row only if a value in a specified field has changed since the last row.
 * 
 */
public class DetectRowChange extends BaseStep implements StepInterface {
  private static Class<?> PKG = DetectRowChangeMeta.class; // for i18n purposes, needed by Translator2!! $NON-NLS-1$

  private DetectRowChangeMeta meta;

  private RowMetaInterface outputRowMeta;

  private int[] valueIndex;

  private Object[] trackedValues;

  private Object[] lastRow;

  private String[] fieldNames;

  private long numRowsSinceLastChange = 0;

  public DetectRowChange( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta,
      Trans trans ) {
    super( stepMeta, stepDataInterface, copyNr, transMeta, trans );
  }

  public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {
    Object[] r = getRow(); // get row, set busy!
    if ( r == null ) {
      // no more input to be expected...
      setOutputDone();
      return false;
    }

    if ( first ) {
      // Save off all metadata, create arrays (now that we know the size), and populate the delta arrays
      meta = (DetectRowChangeMeta) smi;
      outputRowMeta = getInputRowMeta().clone();
      fieldNames = meta.getFieldNames();
      valueIndex = new int[fieldNames.length];
      lastRow = r.clone();
      ArrayList<ValueMetaInterface> trackedValueTypes = new ArrayList<ValueMetaInterface>( meta.findNumTrackedValues() );
      for ( int i = 0; i < fieldNames.length; i++ ) {

        // Store off indexes
        valueIndex[i] = getInputRowMeta().indexOfValue( fieldNames[i] );
        if ( valueIndex[i] == -1 ) {
          throw new KettleException( BaseMessages.getString( PKG, "DetectRowChange.Error.FieldNotFound", fieldNames[i] ) );
        }

        // Add value meta to output row
        ValueMetaInterface valueMeta = getInputRowMeta().getValueMeta( valueIndex[i] );
        if ( valueMeta == null ) {
          throw new KettleException( BaseMessages.getString( PKG, "DetectRowChange.Error.FieldTypeNotFound",
              fieldNames[i] ) );
        }

        // Add changed flag for this field
        outputRowMeta.addValueMeta( new ValueMetaBoolean( valueMeta.getName() + "_changed" ) );

        if ( meta.getIncludeOldValue()[i] ) {
          ValueMetaInterface lastValue = ValueMetaFactory.cloneValueMeta( valueMeta );
          lastValue.setName( valueMeta.getName() + "_last" );
          trackedValueTypes.add( lastValue );
        }
      }

      // Add field for number of rows since last change
      outputRowMeta.addValueMeta( new ValueMetaInteger( "rows_since_last_change" ) );

      // Add the field types for tracked values
      for ( ValueMetaInterface trackedValueType : trackedValueTypes ) {
        outputRowMeta.addValueMeta( trackedValueType );
      }

      trackedValues = new Object[meta.findNumTrackedValues()];

      numRowsSinceLastChange = 0;
      first = false;
      return true;
    }

    // Set up per-row local variables
    boolean changed = false;
    int numInFields = getInputRowMeta().size();
    int newRowLength = numInFields + fieldNames.length + 1;
    Object[] newRow = RowDataUtil.createResizedCopy( r, newRowLength );
    int j = 0;
    numRowsSinceLastChange++;

    // Update boolean changed flags (and store off last values as needed)
    for ( int i = 0; i < fieldNames.length; i++ ) {
      if ( 0 != getInputRowMeta().getValueMeta( valueIndex[i] ).compare( lastRow[valueIndex[i]], r[valueIndex[i]] ) ) {
        // Field changed!
        changed = true;
        newRow[numInFields + i] = Boolean.TRUE;
      } else {
        newRow[numInFields + i] = Boolean.FALSE;
      }
      if ( meta.getIncludeOldValue()[i] ) {
        trackedValues[j++] = lastRow[valueIndex[i]];
      }
    }

    if ( changed ) {
      // Set num rows since last change, then add the tracked "last" values
      newRow[newRowLength - 1] = numRowsSinceLastChange;
      Object[] finalRow = RowDataUtil.addRowData( newRow, newRowLength, trackedValues );
      putRow( outputRowMeta, finalRow ); // copy row to possible alternate rowset(s).
      lastRow = r;
      numRowsSinceLastChange = 0;
    }

    if ( checkFeedback( getLinesRead() ) ) {
      if ( log.isBasic() )
        logBasic( BaseMessages.getString( PKG, "DetectRowChange.Log.LineNumber" ) + getLinesRead() );
    }

    return true;
  }
}
