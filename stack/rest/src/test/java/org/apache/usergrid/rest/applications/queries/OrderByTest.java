/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.usergrid.rest.applications.queries;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.apache.usergrid.rest.AbstractRestIT;
import org.apache.usergrid.rest.TestContextSetup;
import org.apache.usergrid.rest.test.resource.CustomCollection;

import static org.junit.Assert.assertEquals;
import static org.apache.usergrid.utils.MapUtils.hashMap;


/**
 * // TODO: Document this
 *
 * @author ApigeeCorporation
 * @since 4.0
 */
public class OrderByTest extends AbstractRestIT {

    @Rule
    public TestContextSetup context = new TestContextSetup( this );


    @Test
    // USERGRID-1400
    public void orderByShouldNotAffectResults() throws IOException {

        CustomCollection activities = context.customCollection( "activities" );

        long created = 0;
        Map actor = hashMap( "displayName", "Erin" );
        Map props = new HashMap();
        props.put( "actor", actor );
        props.put( "verb", "go" );
        props.put( "content", "bragh" );
        for ( int i = 0; i < 20; i++ ) {
            props.put( "ordinal", i );
            JsonNode activity = activities.create( props );
            if ( i == 5 ) {
                created = activity.findValue( "created" ).longValue();
            }
        }

        refreshIndex(context.getOrgName(), context.getAppName());

        String query = "select * where created > " + created;
        JsonNode node = activities.withQuery( query ).get();
        assertEquals( 10, node.get( "entities" ).size() );

        query = query + " order by created desc";
        node = activities.withQuery( query ).get();
        assertEquals( 10, node.get( "entities" ).size() );
    }


    @Test
    // USERGRID-1520
    public void orderByComesBeforeLimitResult() throws IOException {

        CustomCollection activities = context.customCollection( "activities" );

        Map actor = hashMap( "displayName", "Erin" );
        Map props = new HashMap();
        int checkResultsNum = 0;

        props.put( "actor", actor );
        props.put( "verb", "go" );
        props.put( "content", "bragh" );

        for ( int i = 0; i < 20; i++ ) {
            props.put( "ordinal", i );
            JsonNode activity = activities.create( props );
        }

        refreshIndex(context.getOrgName(), context.getAppName());

        String query = "select * where created > " + 1 + " order by created desc";

        JsonNode incorrectNode = activities.withQuery( query ).withLimit( 5 ).get();

        assertEquals( 5, incorrectNode.get( "entities" ).size() );

        while ( checkResultsNum < 5 ) {
            assertEquals( activities.entityIndex( query, checkResultsNum ),
                    activities.entityIndexLimit( query, 5, checkResultsNum ) );
            checkResultsNum++;
        }
    }

  /*
   * public JsonNode entityIndex(JsonNode container, int index) { return
   * container.get("entities").get(index); }
   */


    @Test
    // USERGRID-1521
    public void orderByReturnCorrectResults() throws IOException {

        CustomCollection activities = context.customCollection( "activities" );

        int size = 200;

        Map<String, String> actor = hashMap( "displayName", "Erin" );
        Map<String, Object> props = new HashMap<String, Object>();

        props.put( "actor", actor );
        props.put( "verb", "go" );
        props.put( "content", "bragh" );

        List<JsonNode> activites = new ArrayList<JsonNode>( size );

        for ( int i = 0; i < size; i++ ) {
            props.put( "ordinal", i );
            JsonNode activity = activities.create( props ).get( "entities" ).get( 0 );
            activites.add( activity );
        }

        refreshIndex(context.getOrgName(), context.getAppName());

        long lastCreated = activites.get( activites.size() - 1 ).get( "created" ).asLong();

        String errorQuery = String.format( "select * where created <= %d order by created desc", lastCreated );
        String cursor = null;
        int index = size - 1;

        do {
            JsonNode response = activities.withQuery( errorQuery ).get();
            JsonNode cursorNode = response.get( "cursor" );

            cursor = cursorNode != null ? cursorNode.asText() : null;

            JsonNode entities = response.get( "entities" );

            int returnSize = entities.size();

            for ( int i = 0; i < returnSize; i++, index-- ) {
                assertEquals( activites.get( index ), entities.get( i ) );
            }

            activities = activities.withCursor( cursor );
        }
        while ( cursor != null && cursor.length() > 0 );

        assertEquals( "Paged to last result", -1, index );
    }
}
