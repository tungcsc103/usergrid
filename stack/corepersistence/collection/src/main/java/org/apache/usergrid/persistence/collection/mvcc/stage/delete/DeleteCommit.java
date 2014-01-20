/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.usergrid.persistence.collection.mvcc.stage.delete;


import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.usergrid.persistence.collection.CollectionScope;
import org.apache.usergrid.persistence.collection.exception.CollectionRuntimeException;
import org.apache.usergrid.persistence.collection.mvcc.MvccEntitySerializationStrategy;
import org.apache.usergrid.persistence.collection.mvcc.MvccLogEntrySerializationStrategy;
import org.apache.usergrid.persistence.collection.mvcc.entity.MvccEntity;
import org.apache.usergrid.persistence.collection.mvcc.entity.MvccLogEntry;
import org.apache.usergrid.persistence.collection.mvcc.entity.ValidationUtils;
import org.apache.usergrid.persistence.collection.mvcc.entity.impl.MvccLogEntryImpl;
import org.apache.usergrid.persistence.collection.mvcc.stage.CollectionIoEvent;
import org.apache.usergrid.persistence.model.entity.Id;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

import rx.util.functions.Func1;


/**
 * This phase should invoke any finalization, and mark the entity 
 * as committed in the data store before returning
 */
@Singleton
public class DeleteCommit implements Func1<CollectionIoEvent<MvccEntity>, Void> {

    private static final Logger LOG = LoggerFactory.getLogger( DeleteCommit.class );

    private final MvccLogEntrySerializationStrategy logStrat;
    private final MvccEntitySerializationStrategy entityStrat;

    @Inject
    public DeleteCommit( final MvccLogEntrySerializationStrategy logStrat,
                         final MvccEntitySerializationStrategy entityStrat ) {

        Preconditions.checkNotNull( 
                logStrat, "logEntrySerializationStrategy is required" );
        Preconditions.checkNotNull( 
                entityStrat, "entitySerializationStrategy is required" );

        this.logStrat = logStrat;
        this.entityStrat = entityStrat;
    }


    @Override
    public Void call( final CollectionIoEvent<MvccEntity> idIoEvent ) {

        final MvccEntity entity = idIoEvent.getEvent();

        ValidationUtils.verifyMvccEntityOptionalEntity( entity );

        final Id entityId = entity.getId();
        final UUID version = entity.getVersion();


        final CollectionScope collectionScope = idIoEvent.getEntityCollection();


        final MvccLogEntry startEntry = new MvccLogEntryImpl( entityId, version,
                org.apache.usergrid.persistence.collection.mvcc.entity.Stage.COMMITTED );

        MutationBatch logMutation = logStrat.write( collectionScope, startEntry );

        //insert a "cleared" value into the versions.  Post processing should actually delete
        MutationBatch entityMutation = entityStrat.clear( collectionScope, entityId, version );

        //merge the 2 into 1 mutation
        logMutation.mergeShallow( entityMutation );


        try {
            logMutation.execute();
        }
        catch ( ConnectionException e ) {
            LOG.error( "Failed to execute write asynchronously ", e );
            throw new CollectionRuntimeException( "Failed to execute write asynchronously ", e );
        }

        /**
         * We're done executing.
         */

        return null;
    }
}
