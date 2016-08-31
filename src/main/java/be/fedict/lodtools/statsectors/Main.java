/*
 * Copyright (c) 2016, Bart Hanssens <bart.hanssens@fedict.be>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package be.fedict.lodtools.statsectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ContentFeatureCollection;
import org.geotools.data.store.ContentFeatureSource;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;

import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.kml.KML;
import org.geotools.kml.KMLConfiguration;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;

import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Fill;
import org.geotools.styling.PolygonSymbolizer;
import org.geotools.styling.Rule;
import org.geotools.styling.Stroke;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.swing.JMapFrame;
import org.geotools.xml.Encoder;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;




/**
 * Convert ShapeFiles to RDF triples
 * 
 * @author Bart Hanssens <bart.hanssens@fedict.be>
 */
public class Main {
    private final static Logger LOG = Logger.getLogger(Main.class.getName());
    
	private final static Model MODEL = new LinkedHashModel();
    private final static ValueFactory FAC = SimpleValueFactory.getInstance();
    
    private final static String PREFIX = "http://data.statbel.fgov.be/statsector/";
    
    /* RDF */
    private final static String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private final static String NS_RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    private final static String NS_SPATIAL = "http://geovocab.org/spatial#";
    private final static String NS_GEO = "http://www.opengis.net/ont/geosparql#";
    private final static String NS_GEOF = "http://www.opengis.net/ont/geosparql#";
    
    private final static String RDFS_LABEL = NS_RDFS + "label";
    private final static String SPATIAL_PP = NS_SPATIAL + "PP";

    /* Properties in Shapefile
    
        "WKT,OBJECTID,Cs012011,Nis_012011,Sec012011,CS102001,CS031991,CS031981,"
        "Sector_nl,Sector_fr,Gemeente,Commune,Arrond_nl,Arrond_fr,Prov_nl,Prov_fr,"
        "Reg_nl,Reg_fr,Nuts1,Nuts2,Nuts3_new,Gis_Perime,Gis_area_h,Cad_area_h";
    */
    private final static String NIS = "Nis_012011";
    private final static String NUTS = "Nut1";
    private final static String NUTS2 = "Nuts2";	
    private final static String NUTS3 = "Nuts3_new";
	private final static String GEMEENTE = "Gemeente";
    private final static String SECTOR = "Cs012011";
    private final static String NAME_NL = "Sector_nl";
    private final static String NAME_FR = "Sector_fr";
    private final static String AREA = "Gis_area_h";
    private final static String PERIM = "Gis_Perime";
    
    

    private static Resource makeURL(String part, Property prop) {
        return (prop != null) 
                ? FAC.createIRI(part + prop.getValue().toString())
                : null;
    }
    
    /**
     * Add triple to model
     * 
     * @param s subject
     * @param p predicate
     * @param o object
     */
    private static void add(Resource s, String p, String o) {
        if (s != null && p != null && o != null) {
            MODEL.add(s, FAC.createIRI(p), FAC.createIRI(o));
        }
    }
    
    private static void add(Resource r, String uri1, String prefix, Property prop) {
        String uri2 = prefix + prop.getValue().toString();
        MODEL.add(r, FAC.createIRI(uri1), FAC.createIRI(uri2));
    }
    
    private static void add(Resource r, String uri1, Property prop, String lang) {
        String str = prop.getValue().toString();
        MODEL.add(r, FAC.createIRI(uri1), FAC.createLiteral(str, lang));
    }
    
    /**
     * Add literal
     * 
     * @param s subject
     * @param p predicate
     * @param val value
     * @param type 
     *//*
    private static void add(Resource s, Property p, String val, RDFDatatype type) {
        MODEL.add(s, p.getValue(), FAC.createLiteral(PERIM, iri)val, type);
    }
    */
    /**
     * Set namespace prefixes
     */
    private static void setPrefixes() {
        MODEL.setNamespace("spatial", NS_SPATIAL);
        MODEL.setNamespace("rdf", NS_RDF);
        MODEL.setNamespace("rdfs", NS_RDFS);    
    }     
    
    /**
     * Converts ShapeFile content to RDF triples.
     * 
     * @param store shapefile
     * @param repo RDF repository
     * @throws IOException
     */
    private static void toRDF(ShapefileDataStore store, RepositoryConnection conn) throws IOException, FactoryException, TransformException {
        ValueFactory vf = conn.getValueFactory();
        
		ListMultimap<String,Geometry> map = ArrayListMultimap.create();
		
        ContentFeatureSource source = store.getFeatureSource();
        ContentFeatureCollection features = source.getFeatures();
        // Also needs the .SHX index file and .DBF database file
        SimpleFeatureIterator iter = features.features();
                    
        while(iter.hasNext()) {
            SimpleFeature feature = iter.next();

            Resource r = makeURL(PREFIX, feature.getProperty(SECTOR));
            if (r != null) {
				System.out.println(feature.getProperty(NAME_NL).getValue());
				MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometryProperty().getValue();
				String nuts3 = feature.getProperty(GEMEENTE).getValue().toString();
				map.put(nuts3, mp);
		
//				System.out.println(type);
				
				//System.out.println(feature.getDefaultGeometryProperty().getType());				
				/*Set<Object> keySet = feature.getDefaultGeometryProperty().getUserData().keySet();
				for(Object key: keySet) {
					System.out.println(key);
				}*/
				/*
                add(r, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                    "http://rdfdata.eionet.europa.eu/ramon/ontology/LAURegion");
                add(r, SPATIAL_PP, "http://nuts.geovocab.org/id/", feature.getProperty(NUTS3));
                add(r, RDFS_LABEL, feature.getProperty(NAME_NL), "nl");
                add(r, RDFS_LABEL, feature.getProperty(NAME_FR), "fr");
                add(r, feature.getProperty(AREA).toString(), "");
                add(r, feature.getProperty(PERIM).toString(), "");
                add(r, feature.getDefaultGeometryProperty().toString(), "");
				*/
            }

//feature.getProperty("AREA"));
			/*addLiteral(r, feature.getProperty(AREA));
			addLiteral(r, feature.getProperty(PERIM));
			addLiteral(r, feature.getDefaultGeometryProperty());*/
        }
		
		CoordinateReferenceSystem WGS = CRS.decode("EPSG:4326",true);
		CoordinateReferenceSystem lambert = CRS.decode("EPSG:31300",true);
		MathTransform convertToMeter = CRS.findMathTransform(WGS, lambert,false);
		MathTransform convertFromMeter = CRS.findMathTransform(lambert, WGS,false);


		GeometryFactory factory = JTSFactoryFinder.getGeometryFactory( null );
		Encoder enc = new Encoder(new KMLConfiguration());
		FileOutputStream fos = new FileOutputStream("c:\\data\\out.kml");
		enc.setIndenting(true);
		
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName("Simple");
		sftBuilder.add("name", String.class);
		sftBuilder.add("geom", Geometry.class);
		SimpleFeatureType feature = sftBuilder.buildFeatureType();
		ListFeatureCollection fc = new ListFeatureCollection(feature);
			
		 int i = 0;
     // note the following geometry collection may be invalid (say with overlapping polygons)
		for(String s: map.keySet()) {
			List<Geometry> geoms = map.get(s);
			Geometry geom = factory.buildGeometry(geoms).union();
			Geometry target = JTS.transform(geom, convertFromMeter);
		
			SimpleFeatureBuilder builder = new SimpleFeatureBuilder(feature);
			builder.set("name", s);
			builder.set("geom", target);
			
			
			SimpleFeature simple = builder.buildFeature(s);
			fc.add(simple);
			
		//	System.out.println(geom.toString());
		//f (geom instanceof Polygon) {
			//nc.encode(geom, KML.Polygon, fos);
//			} else if (geom instanceof MultiPolygon) {
//				enc.encode(geom, , fos);
		//
		//	System.out.println(s);
		}
		enc.encode(fc, KML.kml, fos);
		fos.close();
		
		MapContent mapc = new MapContent();

		Style style = createStyle();
		style.featureTypeStyles();
		Layer layer = new FeatureLayer(fc, style);
		mapc.addLayer(layer);
		
		JMapFrame.showMap(mapc);
		
	}		
    
	
	 private static Style createStyle() {
		StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory();
		FilterFactory filterFactory = CommonFactoryFinder.getFilterFactory();

        // create a partially opaque outline stroke
        Stroke stroke = styleFactory.createStroke(
                filterFactory.literal(Color.BLUE),
                filterFactory.literal(1),
                filterFactory.literal(0.5));

        // create a partial opaque fill
        Fill fill = styleFactory.createFill(
                filterFactory.literal(Color.CYAN),
                filterFactory.literal(0.5));

        /*
         * Setting the geometryPropertyName arg to null signals that we want to
         * draw the default geomettry of features
         */
        PolygonSymbolizer sym = styleFactory.createPolygonSymbolizer(stroke, fill, null);

        Rule rule = styleFactory.createRule();
        rule.symbolizers().add(sym);
        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle(new Rule[]{rule});
        Style style = styleFactory.createStyle();
        style.featureTypeStyles().add(fts);

        return style;
    }
    /**
     * Main
     * 
     * @param args 
     */
    public static void main(String[] args) throws FactoryException, TransformException {
     /*   if (args.length != 2) {
            System.err.println("Usage: <SHP input file> <RDF output file>");
            System.exit(-1);
        }
        
        File fin = new File(args[0]);
        File fout = new File(args[1]);
        */
		File fin = new File("C:\\Data\\statsector\\scbel01012011_gen13.shp");
		
        Repository repo = new SailRepository(new MemoryStore());
        repo.initialize();
        
        try (RepositoryConnection conn = repo.getConnection()) {
			ShapefileDataStore store = new ShapefileDataStore(fin.toURI().toURL());
            store.setCharset(StandardCharsets.UTF_8);
            
           toRDF(store, conn);
        
            store.dispose();
           
           /* BufferedOutputStream buf = new BufferedOutputStream(new FileOutputStream(fout));
            RDFHandler handler = Rio.createWriter(RDFFormat.TURTLE, buf);
            conn.export(handler);
            */
        } catch (MalformedURLException ex) {
            LOG.severe("Could not open file");
            System.exit(-2);
        } catch (IOException ex) {
            LOG.severe("IO error processing");
            System.exit(-3);
        }
        
        repo.shutDown();
    }
}
