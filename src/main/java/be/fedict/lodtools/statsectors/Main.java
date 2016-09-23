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

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ContentFeatureCollection;
import org.geotools.data.store.ContentFeatureSource;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.GEO;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;


/**
 * Convert ShapeFiles to RdfConverter triples
 * 
 * @author Bart Hanssens <bart.hanssens@fedict.be>
 */
public class Main {
    private final static Logger LOG = Logger.getLogger(Main.class.getName());
    
	private final static Model MODEL = new LinkedHashModel();
    private final static ValueFactory FAC = SimpleValueFactory.getInstance();
    
	/* Prefixes */
	private final static String PREF_NIS = "http://geo.belgif.org/nis2011/";
	private final static String PREF_NUTS = "http://nuts.geovocab.org/id/";
	
	/* Name spaces */
    private final static String NS_RAMON = "http://ec.europa.eu/eurostat/ramon/ontologies/geographic.rdf#";
    private final static String NS_SPATIAL = "http://geovocab.org/spatial#";
    
	/* Properties */
    private final static IRI SPATIAL_PP = FAC.createIRI(NS_SPATIAL + "PP");
	private final static IRI LAU_REG = FAC.createIRI(NS_RAMON + "LAURegion");
	private final static IRI NUTS_REG = FAC.createIRI(NS_RAMON + "NUTSRegion");
	

    /* Properties in Shapefile
    
        "WKT,OBJECTID,Cs012011,Nis_012011,Sec012011,CS102001,CS031991,CS031981,"
        "Sector_nl,Sector_fr,Gemeente,Commune,Arrond_nl,Arrond_fr,Prov_nl,Prov_fr,"
        "Reg_nl,Reg_fr,Nuts1,Nuts2,Nuts3_new,Gis_Perime,Gis_area_h,Cad_area_h";
    */
    private final static String NIS = "Nis_012011";
    private final static String NUTS = "Nut1";
    private final static String NUTS2 = "Nuts2";	
    private final static String NUTS3 = "Nuts3_new";
	private final static String GEMEENTE_NL = "Gemeente";
	private final static String GEMEENTE_FR = "Commune";
    private final static String SECTOR = "Cs012011";
    private final static String NAME_NL = "Sector_nl";
    private final static String NAME_FR = "Sector_fr";
    private final static String AREA = "Gis_area_h";
    private final static String PERIM = "Gis_Perime";
    

	/**
	 * 
	 * @param part
	 * @param prop
	 * @return 
	 */
    private static Resource makeURL(String part, String prop) {
        return (prop != null) ? FAC.createIRI(part + prop + "#id") : null;
    }

	/**
	 * Get string value from geo feature
	 * 
	 * @param feat
	 * @param name
	 * @return 
	 */
	private static String makeStr(SimpleFeature feat, String name) {
		return feat.getProperty(name).getValue().toString();
	}
	
	/**
	 * Get string value from default geo
	 * 
	 * @param feat
	 * @return 
	 */
	private static String makeStrGeo(SimpleFeature feat) {
		return feat.getDefaultGeometryProperty().getValue().toString();
	}
			
    /**
     * Converts ShapeFile content to RdfConverter triples.
     * 
     * @param store shapefile
     * @throws IOException
     */
    private static void toRDF(ShapefileDataStore store) throws IOException {
        //ListMultimap<String,Geometry> map = ArrayListMultimap.create();
		
        ContentFeatureSource source = store.getFeatureSource();
        ContentFeatureCollection features = source.getFeatures();
        
        // Also needs the .SHX index file and .DBF database file
        SimpleFeatureIterator iter = features.features();
        
		/* Generate sectors */
        while(iter.hasNext()) {
            SimpleFeature feature = iter.next();

            Resource sect = makeURL(PREF_NIS, makeStr(feature, SECTOR));
            if (sect != null) {
				//MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometryProperty().getValue();
				//String nuts3 = feature.getProperty(GEMEENTE).getValue().toString();
				//map.put(nuts3, mp);
				MODEL.add(sect, RDF.TYPE, LAU_REG);
                MODEL.add(sect, SPATIAL_PP, makeURL(PREF_NUTS, makeStr(feature, NUTS3)));
				MODEL.add(sect, SPATIAL_PP, makeURL(PREF_NIS, makeStr(feature, NIS).replace(".0", "")));
                MODEL.add(sect, RDFS.LABEL, 
						FAC.createLiteral(makeStr(feature, NAME_NL), "nl"));
                MODEL.add(sect, RDFS.LABEL, 
						FAC.createLiteral(makeStr(feature, NAME_FR), "fr"));
				MODEL.add(sect, GEO.AS_WKT, 
						FAC.createLiteral(makeStrGeo(feature), GEO.WKT_LITERAL));
			}
		}
	}

    /**
     * Main
     * 
     * @param args 
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: <SHP input file> <RDF output file>");
            System.exit(-1);
        }
       
	//	File fin = new File("C:\\Data\\statsector\\scbel01012011_gen13.shp");
	
		File fin = new File(args[0]);		
		File fout = new File(args[1]);
        
		
        try {
			ShapefileDataStore store = new ShapefileDataStore(fin.toURI().toURL());
            store.setCharset(Charsets.UTF_8);
            toRDF(store);
            
			store.getFeatureReader().close();
			store.dispose();
		   
			Writer buf = new OutputStreamWriter(new FileOutputStream(fout), Charsets.UTF_8);
			Rio.write(MODEL, buf, RDFFormat.NTRIPLES);
        } catch (MalformedURLException ex) {
            LOG.severe("Could not open file");
            System.exit(-2);
        } catch (IOException ex) {
            LOG.severe("IO error processing");
            System.exit(-3);
        }
    }
}

//feature.getProperty("AREA"));
			/*addLiteral(sect, feature.getProperty(AREA));
			addLiteral(sect, feature.getProperty(PERIM));
			addLiteral(sect, feature.getDefaultGeometryProperty());*/
        
		
	/*	CoordinateReferenceSystem WGS = CRS.decode("EPSG:4326",true);
	
		CoordinateReferenceSystem lambert = CRS.decode("EPSG:31300",true);
		MathTransform convertToMeter = CRS.findMathTransform(WGS, lambert,false);
		MathTransform convertFromMeter = CRS.findMathTransform(lambert, WGS,false);
*/
//		GeometryFactory factory = JTSFactoryFinder.getGeometryFactory( null );
		// Encoder enc = new Encoder(new KMLConfiguration());
		//FileOutputStream fos = new FileOutputStream("c:\\data\\out.kml");
		// enc.setIndenting(true);
		
		/*SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
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
	//		Geometry target = JTS.transform(geom, convertFromMeter);
		
			SimpleFeatureBuilder builder = new SimpleFeatureBuilder(feature);
			builder.set("name", s);
	//		builder.set("geom", target);
			builder.set("geom", geom);
			
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
		//enc.encode(fc, KML.kml, fos);
		//fos.close();
		/*
		MapContent mapc = new MapContent();

		Style style = createStyle();
		style.featureTypeStyles();
		Layer layer = new FeatureLayer(fc, style);
		mapc.addLayer(layer);
		
		JMapFrame.showMap(mapc);
		*/
    
/*	
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

*/        /*
         * Setting the geometryPropertyName arg to null signals that we want to
         * draw the default geomettry of features
         */
  /*      PolygonSymbolizer sym = styleFactory.createPolygonSymbolizer(stroke, fill, null);

        Rule rule = styleFactory.createRule();
        rule.symbolizers().add(sym);
        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle(new Rule[]{rule});
        Style style = styleFactory.createStyle();
        style.featureTypeStyles().add(fts);

        return style;
    }
*/
