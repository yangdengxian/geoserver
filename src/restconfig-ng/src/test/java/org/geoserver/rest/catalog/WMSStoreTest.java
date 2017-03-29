/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.rest.catalog;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathExists;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.rest.WMSStoreController;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.rest.RestBaseController;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import org.springframework.mock.web.MockHttpServletResponse;


public class WMSStoreTest extends CatalogRESTTestSupport {
    
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        
        // we need to add a wms store
        CatalogBuilder cb = new CatalogBuilder(catalog);
        cb.setWorkspace(catalog.getWorkspaceByName("sf"));
        WMSStoreInfo wms = cb.buildWMSStore("demo");
        wms.setCapabilitiesURL("http://demo.opengeo.org/geoserver/wms?");
        catalog.add(wms);
    } 
    
    @Test
    public void testBeanPresent() throws Exception {
        assertThat(GeoServerExtensions.extensions(RestBaseController.class), 
            hasItem(instanceOf(WMSStoreController.class)));
    }
    
    @Test
    public void testGetAllAsXML() throws Exception {
        Document dom = getAsDOM( "/restng/workspaces/sf/wmsstores.xml");
        assertEquals("wmsStores", dom.getDocumentElement().getNodeName());
        assertEquals( catalog.getStoresByWorkspace( "sf", WMSStoreInfo.class ).size(), 
            dom.getElementsByTagName( "wmsStore").getLength() );
    }

    @Test
    public void testGetAllAsJSON() throws Exception {
        JSON json = getAsJSON( "/restng/workspaces/sf/wmsstores.json");
        assertTrue( json instanceof JSONObject );
        
        Object stores = ((JSONObject)json).getJSONObject("wmsStores").get("wmsStore");
        assertNotNull( stores );
        
        if( stores instanceof JSONArray ) {
            assertEquals( catalog.getStoresByWorkspace("sf", WMSStoreInfo.class).size() , ((JSONArray)stores).size() );    
        } else {
            assertEquals( 1, catalog.getStoresByWorkspace("sf", WMSStoreInfo.class).size() );
        }
    }
    
    @Ignore // FIXME Enable when HTML is working
    @Test
    public void testGetAllAsHTML() throws Exception {
        Document dom = getAsDOM( "/restng/workspaces/sf/wmsstores.html");
        List<WMSStoreInfo> stores = catalog.getStoresByWorkspace("sf", WMSStoreInfo.class);
        
        NodeList links = xp.getMatchingNodes("//html:a", dom );
        assertEquals( stores.size(), links.getLength() );
        
        for ( int i = 0; i < stores.size(); i++ ){
            WMSStoreInfo store = stores.get( i );
            Element link = (Element) links.item( i );
            
            assertTrue( link.getAttribute("href").endsWith( store.getName() + ".html") );
        }
    }
    
    @Test
    public void testPutAllUnauthorized() throws Exception {
        assertEquals( 405, putAsServletResponse("/restng/workspaces/sf/wmsstores").getStatus() );
    }

    @Test
    public void testDeleteAllUnauthorized() throws Exception {
        assertEquals( 405, deleteAsServletResponse("/restng/workspaces/sf/wmsstores").getStatus() );
    }

    @Test
    public void testGetAsXML() throws Exception {
        Document dom = getAsDOM( "/restng/workspaces/sf/wmsstores/demo.xml");
        assertEquals( "wmsStore", dom.getDocumentElement().getNodeName() );
        assertEquals( "demo", xp.evaluate( "/wmsStore/name", dom) );
        assertEquals( "sf", xp.evaluate( "/wmsStore/workspace/name", dom) );
        assertXpathExists( "/wmsStore/capabilitiesURL", dom );
    }

    @Ignore // FIXME Enable when HTML is working
    @Test
    public void testGetAsHTML() throws Exception {
        Document dom = getAsDOM( "/restng/workspaces/sf/wmsstores/demo.html");
        
        WMSStoreInfo wms = catalog.getStoreByName( "demo", WMSStoreInfo.class );
        List<WMSLayerInfo> wmsLayers = catalog.getResourcesByStore( wms, WMSLayerInfo.class );
        
        NodeList links = xp.getMatchingNodes("//html:a", dom );
        assertEquals( wmsLayers.size(), links.getLength() );
        
        for ( int i = 0; i < wmsLayers.size(); i++ ){
            WMSLayerInfo wl = wmsLayers.get( i );
            Element link = (Element) links.item( i );
            
            assertTrue( link.getAttribute("href").endsWith( wl.getName() + ".html") );
        }
    }
    
    @Test
    public void testGetWrongWMSStore() throws Exception {
        // Parameters for the request
        String ws = "sf";
        String wms = "sfssssss";
        // Request path
        String requestPath = "/restng/workspaces/" + ws + "/wmsstores/" + wms + ".html";
        // Exception path
        String exception = "No such wms store: " + ws + "," + wms;
        // First request should thrown an exception
        MockHttpServletResponse response = getAsServletResponse(requestPath);
        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains(
                exception));
        // Same request with ?quietOnNotFound should not throw an exception
        response = getAsServletResponse(requestPath + "?quietOnNotFound=true");
        assertEquals(404, response.getStatus());
        assertFalse(response.getContentAsString().contains(
                exception));
        // No exception thrown
        assertTrue(response.getContentAsString().isEmpty());
    }
    
    Matcher<HttpServletResponse> hasStatus(int code) {
        return hasProperty("status", is(code));
    }
    
    Matcher<HttpServletResponse> hasHeader(String name, Matcher<String> valueMatcher) {
        return new BaseMatcher<HttpServletResponse>(){
            
            @Override
            public boolean matches(Object item) {
                if(item instanceof HttpServletResponse) {
                    String value = ((HttpServletResponse) item).getHeader(name);
                    if(Objects.isNull(value)) {
                        return false;
                    } else {
                        return valueMatcher.matches(value);
                    }
                } else {
                    return false;
                }
            }
            
            @Override
            public void describeTo(Description description) {
                description
                    .appendText("HTTP Response with header ")
                    .appendValue(name)
                    .appendText(" with value ")
                    .appendDescriptionOf(valueMatcher);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if(item instanceof HttpServletResponse) {
                    String value = ((HttpServletResponse) item).getHeader(name);
                    if(Objects.isNull(value)) {
                        description.appendText("did not have header ").appendValue("name");
                    } else {
                        description.appendText("header ").appendValue(name).appendText(" ");
                        valueMatcher.describeMismatch(value, description);
                    }
                } else {
                    description.appendText("was not an HttpServeletResponse");
                }
            }
        };
    }
    
    @Test
    public void testPostAsXML() throws Exception {
        
        String xml =
            "<wmsStore>" +
              "<name>newWMSStore</name>" +
              "<capabilitiesURL>http://somehost/wms?</capabilitiesURL>" +
              "<workspace>sf</workspace>" + 
            "</wmsStore>";
        MockHttpServletResponse response = 
            postAsServletResponse( "/restng/workspaces/sf/wmsstores", xml, "text/xml" );
        
        assertThat(response, hasStatus(201) );
        assertThat(response, hasHeader("Location", endsWith("/workspaces/sf/wmsstores/newWMSStore")) );

        WMSStoreInfo newStore = catalog.getStoreByName( "newWMSStore", WMSStoreInfo.class );
        assertNotNull( newStore );
        
        assertEquals("http://somehost/wms?", newStore.getCapabilitiesURL());
    }

    @Test
    public void testGetAsJSON() throws Exception {
        JSON json = getAsJSON( "/restng/workspaces/sf/wmsstores/demo.json" );
        
        JSONObject store = ((JSONObject)json).getJSONObject("wmsStore");
        assertNotNull(store);
        
        assertEquals( "demo", store.get( "name") );
        assertEquals( "sf", store.getJSONObject( "workspace").get( "name" ) );
        assertEquals( "http://demo.opengeo.org/geoserver/wms?", store.getString( "capabilitiesURL") );
    }

    @Test
    public void testPostAsJSON() throws Exception {
        removeStore("sf", "newWMSStore");
        String json = 
            "{'wmsStore':{" +
               "'capabilitiesURL': 'http://somehost/wms?'," +
                "'workspace':'sf'," +
                "'name':'newWMSStore'," +
              "}" +
            "}";
        MockHttpServletResponse response = 
            postAsServletResponse( "/restng/workspaces/sf/wmsstores", json, "text/json" );
        
        assertEquals( 201, response.getStatus() );
        assertNotNull( response.getHeader( "Location") );
        assertTrue( response.getHeader("Location").endsWith( "/workspaces/sf/wmsstores/newWMSStore" ) );

        WMSStoreInfo newStore = catalog.getStoreByName( "newWMSStore", WMSStoreInfo.class );
        assertNotNull( newStore );
        
        assertEquals("http://somehost/wms?", newStore.getCapabilitiesURL());
    }

    @Test
    public void testPostToResource() throws Exception {
        String xml = 
        "<wmsStore>" + 
         "<name>demo</name>" + 
         "<enabled>false</enabled>" + 
        "</wmsStore>";
        
        MockHttpServletResponse response = 
            postAsServletResponse( "/restng/workspaces/sf/wmsstores/demo", xml, "text/xml");
        assertEquals( 405, response.getStatus() );
    }

    @Test
    public void testPut() throws Exception {
        Document dom = getAsDOM( "/restng/workspaces/sf/wmsstores/demo.xml");
        assertXpathEvaluatesTo("true", "/wmsStore/enabled", dom );
        
        String xml = 
        "<wmsStore>" + 
         "<name>demo</name>" + 
         "<enabled>false</enabled>" + 
        "</wmsStore>";
        
        MockHttpServletResponse response = 
            putAsServletResponse( "/restng/workspaces/sf/wmsstores/demo", xml, "text/xml");
        assertEquals( 200, response.getStatus() );

        dom = getAsDOM( "/restng/workspaces/sf/wmsstores/demo.xml");
        assertXpathEvaluatesTo("false", "/wmsStore/enabled", dom );
        
        assertFalse( catalog.getStoreByName("sf", "demo", WMSStoreInfo.class).isEnabled() );
    }
    
    @Test
    public void testPutNonDestructive() throws Exception {
        WMSStoreInfo wsi = catalog.getStoreByName("sf", "demo", WMSStoreInfo.class);
        wsi.setEnabled(true);
        catalog.save(wsi);
        assertTrue(wsi.isEnabled());
        int maxConnections = wsi.getMaxConnections();
        int readTimeout = wsi.getReadTimeout();
        int connectTimeout = wsi.getConnectTimeout();
        boolean useConnectionPooling = wsi.isUseConnectionPooling();
        
        String xml = 
            "<wmsStore>" + 
            "<name>demo</name>" + 
            "</wmsStore>";

        MockHttpServletResponse response = 
            putAsServletResponse("/restng/workspaces/sf/wmsstores/demo", xml, "text/xml" );
        assertEquals( 200, response.getStatus() );
        
        wsi = catalog.getStoreByName("sf", "demo", WMSStoreInfo.class);
        
        assertTrue(wsi.isEnabled());
        assertEquals(maxConnections, wsi.getMaxConnections());
        assertEquals(readTimeout, wsi.getReadTimeout());
        assertEquals(connectTimeout, wsi.getConnectTimeout());
        assertEquals(useConnectionPooling, wsi.isUseConnectionPooling());
    }

    @Test
    public void testPutNonExistant() throws Exception {
        String xml = 
            "<wmsStore>" + 
            "<name>changed</name>" + 
            "</wmsStore>";

        MockHttpServletResponse response = 
            putAsServletResponse("/restng/workspaces/sf/wmsstores/nonExistant", xml, "text/xml" );
        assertEquals( 404, response.getStatus() );
    }

    @Test
    public void testDeleteNonExistant() throws Exception {
        assertEquals( 404, deleteAsServletResponse("/restng/workspaces/sf/datastores/nonExistant").getStatus() );
    }

    @Test
    public void testDelete() throws Exception {
        removeStore("sf", "newWMSStore");
        testPostAsXML();
        assertNotNull( catalog.getStoreByName("sf", "newWMSStore", WMSStoreInfo.class));
        
        assertEquals( 200, deleteAsServletResponse("/restng/workspaces/sf/wmsstores/newWMSStore").getStatus());
        assertNull( catalog.getStoreByName("sf", "newWMSStore", WMSStoreInfo.class));
    }
    
//    public void testDeleteNonEmptyForbidden() throws Exception {
//        assertEquals( 403, deleteAsServletResponse("/restng/workspaces/sf/datastores/sf").getStatusCode());
//    }
    
    @Test
    public void testPutNameChangeForbidden() throws Exception {
        String xml = "<wmsStore>" +
            "<name>newName</name>" + 
            "</wmsStore>";
        assertEquals( 403, putAsServletResponse("/restng/workspaces/sf/wmsstores/demo", xml, "text/xml").getStatus());
    }

    @Test
    public void testPutWorkspaceChangeForbidden() throws Exception {
        String xml = "<wmsStore>" +
        "<workspace>gs</workspace>" + 
        "</wmsStore>";
        MockHttpServletResponse response = putAsServletResponse("/restng/workspaces/sf/wmsstores/demo", xml, "text/xml");
        assertThat(response, hasStatus(403));
    }
}