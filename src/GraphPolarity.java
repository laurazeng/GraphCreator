package graphcreator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder;
import org.gephi.filters.plugin.graph.GiantComponentBuilder;
import org.gephi.filters.plugin.operator.INTERSECTIONBuilder.IntersectionOperator;
import org.gephi.filters.plugin.partition.PartitionBuilder;
import org.gephi.filters.plugin.partition.PartitionBuilder.NodePartitionFilter;
import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.io.generator.plugin.RandomGraph;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerFactory;
import org.gephi.io.importer.api.EdgeDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.partition.api.Part;
import org.gephi.partition.api.Partition;
import org.gephi.partition.api.PartitionController;
import org.gephi.partition.plugin.NodeColorTransformer;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.Ranking;
import org.gephi.ranking.api.RankingController;
import org.gephi.ranking.api.Transformer;
import org.gephi.ranking.plugin.transformer.AbstractSizeTransformer;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;




/**
 *
 * @authors Samantha and Laura
 */

/**
 * to speed things up, 
 * load graph once
 * export only what's necessary
 * 
 */

public class GraphPolarity { 
    private final ProjectController pc;
    private final Workspace workspace;
    final ImportController importController;        
    final GraphModel graphModel;
    final AttributeController attributeController;
    final AttributeModel attributeModel;        
    final RankingController rankingController;
    final PartitionController partitionController;        
    final FilterController filterController;
    final PreviewController previewController;
    final PreviewProperty previewProperty; 
    final ExportController ec;
    final NodeColorTransformer nodeColorTransformer;
    final PNGExporter pngExporter;
    final ByteArrayOutputStream baos;
    final Iterator<Color> colors;
    private HashMap<Integer,HashMap<String,Double>> centers;
            
    
    public GraphPolarity() { 
        pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        workspace = pc.getCurrentWorkspace();
        importController = Lookup.getDefault().lookup(ImportController.class); 
        graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        attributeController = Lookup.getDefault().lookup(AttributeController.class);
        attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel(); 
        rankingController = Lookup.getDefault().lookup(RankingController.class);
        partitionController = Lookup.getDefault().lookup(PartitionController.class);
        filterController = Lookup.getDefault().lookup(FilterController.class);
        previewController = Lookup.getDefault().lookup(PreviewController.class);
        previewProperty = Lookup.getDefault().lookup(PreviewProperty.class);
        ec = Lookup.getDefault().lookup(ExportController.class);
        nodeColorTransformer = new NodeColorTransformer();
        pngExporter = (PNGExporter) ec.getExporter("png");
        baos = new ByteArrayOutputStream();
        colors = nodeColorTransformer.getMap().values().iterator();
        centers = new HashMap<Integer,HashMap<String,Double>>(); 
    }

    
    public Graph import_csv(String inputFile, Boolean directed) {
        Container container;
        try {
            File file = new File(inputFile);
            container = importController.importFile(file);
            if (directed)
                container.getLoader().setEdgeDefault(EdgeDefault.DIRECTED);   //Force DIRECTED
            else
                container.getLoader().setEdgeDefault(EdgeDefault.UNDIRECTED);   //Force UNDIRECTED
            container.setAllowAutoNode(true);  //create missing nodes
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);  
        return graphModel.getGraph();
    }
    
    /** creates the Graph from a .gexf file (specified in params)
     * it's nice to have this method so we don't have to keep 
     * reloading the same Graph object (good for multiple runs of k means)
     * 
     * @param inputFile - the .gexf file
     * @return Graph object representing the inputFile
     */
    public Graph import_gexf(String inputFile) {
        Container container;
        try {
            File file = new File(inputFile);
            container = importController.importFile(file);
        } catch (FileNotFoundException fnfe) {
            container = null;
        }
        importController.process(container, new DefaultProcessor(), workspace);        
        return graphModel.getGraph();
    }
    
    public void label_degrees(Graph graph) { 
        for (Node n : graph.getNodes()) { 
            int degree = graph.getDegree(n);
            n.getAttributes().setValue("degree", degree);
        }
    }
    
    public void prep_graph(Graph graph) { 
        // filter out anything that doesn't have degree gt inputed degreeFilter
        int degreeFilter=1;
        // instaniate the final graph filter and view 
        Query query;
        GraphView view;
        DegreeRangeBuilder.DegreeRangeFilter degreeRangeFilter = new DegreeRangeBuilder.DegreeRangeFilter();
        degreeRangeFilter.init(graphModel.getGraph());
        degreeRangeFilter.setRange(new Range(degreeFilter, Integer.MAX_VALUE));     //Remove nodes with degree < 10
        Query degreeQuery = filterController.createQuery(degreeRangeFilter);
        view = filterController.filter(degreeQuery);
        query = degreeQuery;
        GiantComponentBuilder.GiantComponentFilter giantComp = 
            new GiantComponentBuilder.GiantComponentFilter();
        giantComp.init(graphModel.getGraph());
        Query giantCompQuery = filterController.createQuery(giantComp);
        filterController.setSubQuery(giantCompQuery,degreeQuery);
        
        // overwrite the resulting query and view with the giant component/degree subfilter
        view = filterController.filter(giantCompQuery);
        query = giantCompQuery;
        graphModel.setVisibleView(view);
        /*Graph g = graphModel.getGraphVisible();
        
        System.out.println("Nodes: " + g.getNodeCount());
        System.out.println("Edges: " + g.getEdgeCount());*/
        
        
        // rank nodes by degree
        System.out.println("Ranking nodes by degree");
        Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
        AbstractSizeTransformer sizeTransformer = 
                (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(3);
        sizeTransformer.setMaxSize(20);
        rankingController.transform(degreeRanking,sizeTransformer);
        
        
        //run force atlas
        int iterations = 5000;
        ForceAtlas2 f2 = new ForceAtlas2Builder().buildLayout();
        f2.setJitterTolerance(.01);
        f2.setGravity(1.0);
        f2.setScalingRatio(2.0);
        f2.setEdgeWeightInfluence(0.0);
        f2.setAdjustSizes(true);
        f2.setGraphModel(graphModel);
        f2.initAlgo();
        for (int i = 0; i < iterations && f2.canAlgo(); i++) {
            f2.goAlgo();
            if (i%1000 == 0 && i != 0) {
                PreviewModel previewModel = previewController.getModel();
                previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 40);
                previewController.refreshPreview();
                System.out.println(i + " iterations completed");
            }
        }
        f2.endAlgo();
        
        // run ForceAtlas2 a second time to fix overlapping nodes
        for (int j=0; j<10; j++) {
            f2.setAdjustSizes(true);
            f2.initAlgo();
            for (int i = 0; i < 10 && f2.canAlgo(); i++) 
                f2.goAlgo();
            f2.endAlgo();
        }
        
        graphModel.getGraphVisible();
    }
    
    /** this is the initial step in k means where every node is assigned
     * a random cluster value from 0 to k-1
     * @param graph - Graph object from inputFile
     * @param k - number of clusters (from k means algo)
     */
    public void prep_kmeans(Graph graph, int k) {
        // SEED: randomly assign each node to a cluster (k clusters)
        Random r = new Random(); 
        for (Node n : graph.getNodes()) {
            //assign clusters randomly
            n.getAttributes().setValue("cluster", r.nextInt(k));
            n.getAttributes().setValue("center",false);
            //System.out.println(n.getAttributes().getValue("cluster"));
        }
        //for all groups k, assign the int k and make the center null
        //center will be filled out in run_kmeans() method
        for (int i=0; i<k; i++)
            centers.put(i,null);
        System.out.println(centers);
    }
    
    /** does the iterative part of the k means algo where we calc the mean
     * centers for each cluster, reassign the k clusters based on minimum
     * distance, and then repeat calculating the mean centers and reassigning
     * 
     * could improve by changing iterations to threshold (instead of arbitrarily 
     * choosing how many iterations, just repeat the process until the distance
     * between the prev center and cur center is less than an arbitrary threshold)
     * 
     * @param graph - same Graph from import_file
     * @param k - num of k clusters
     */
    public void run_kmeans(Graph graph, int k) { 
        // iterate       
        int iterations = 5000;
        for (int i=0; i<iterations; i++) { 
            //calculate the mean center for the clusters
            for (Node n : graph.getNodes()) {
                try { 
                    int cluster = (int) n.getAttributes().getValue("cluster");

                    double xco = n.getNodeData().x();
                    double yco = n.getNodeData().y();
                    //if this is our first time adding, then we just want to add
                    //the value x (running the mean formula becomes problematic 
                    //b/c num=0, and we don't have values for oldX, oldY, etc. 
                    if (centers.get(cluster)==null) { 
                        HashMap<String,Double> center = new HashMap<String,Double>(); 
                        center.put("x", xco); 
                        center.put("y", yco);
                        //num represents the number of nodes belong to 
                        //this cluster--useful for finding means
                        center.put("num",1.0);
                        centers.put(cluster,center); 
                        //System.out.println("Adding for the first time to cluster: " + cluster); 
                    } else { 
                        HashMap<String,Double> oldCenter = centers.get(cluster);
                        double oldNum = oldCenter.get("num");
                        //System.out.println("oldNum is " + oldNum);
                        double oldX = oldCenter.get("x"); 
                        double oldY = oldCenter.get("y");
                        double avgX = oldX*(oldNum/(oldNum+1))+xco*(1/(oldNum+1));
                        double avgY = oldY*(oldNum/(oldNum+1))+yco*(1/(oldNum+1));
                        HashMap<String,Double> center = new HashMap<String,Double>(); 
                        center.put("x",avgX);
                        center.put("y",avgY);
                        center.put("num",oldNum+1.0);
                        centers.put(cluster,center); 
                    }
                    /*System.out.println("node -- cluster: " + cluster 
                            + ", x: " + xco + ", y: " + yco);*/
                } catch (NullPointerException npe) { 
                    //this'll only happen if we run k means after we've added centers
                    //b/c center nodes don't have cluster values
                    System.out.println("node: " + n); 
                }
            }
            //System.out.println(centers);
            //System.out.println(centers.get(0));

            //reset the num of ea cluster to 0 b/c we're reassigning all clusters
            for (int j=0; j<k; j++) { 
                try { 
                    centers.get(j).put("num",0.0);
                } catch(Exception e) { 
                    //there will be cases where a center will not be assigned to any nodes
                    //meaning the x and y coords are never assigned and the value for this center
                    //is still null
                    //this really shouldn't happen unless our data set is super small 
                    //or k is super large
                    //let's assign everything to 0.0
                    int cluster = j; 
                    HashMap<String,Double> center = new HashMap(); 
                    center.put("x",0.0); 
                    center.put("y",0.0);
                    center.put("num",0.0);
                    centers.put(cluster, center);
                    
                    System.out.println("Run kmeans error: " + e);
                    System.out.println("Centers: " + centers);
                    System.out.println("j: " + j);
                }
            }

            //System.out.println("centers after we reset: "); 
            //System.out.println(centers);

            //assigning node to closest cluster
            for (Node n: graph.getNodes()) { 
                double xco = n.getNodeData().x();
                double yco = n.getNodeData().y();
                double min_distance=Double.MAX_VALUE;
                //go through all groups and see which one has the best distance
                for (int j=0; j<k; j++) { 
                    HashMap<String,Double> center = centers.get(j);
                    double xcent=center.get("x"); 
                    double ycent=center.get("y"); 
                    double distance = Math.sqrt(Math.pow((xco-xcent),2)+Math.pow((yco-ycent),2));
                    if (distance<min_distance) {
                        //there's a new min_distance
                        min_distance=distance; 
                        //also, we should change the cluster and dist_from_cent 
                        //values to reflect this
                        n.getAttributes().setValue("cluster", j);
                        n.getAttributes().setValue("dist_from_cent", distance);
                    }
                }
                int cluster = (int)n.getAttributes().getValue("cluster"); 
                double oldNum = centers.get(cluster).get("num"); 
                //need to update how many nodes are in this cluster
                centers.get(cluster).put("num",oldNum+1.0);
                
            }
            
        }
        // partition nodes (color them) by cluster        
        Partition p = partitionController.buildPartition(attributeModel.getNodeTable().getColumn("cluster"), graph);
        nodeColorTransformer.randomizeColors(p);
        partitionController.transform(p, nodeColorTransformer);

        System.out.println("After everything, here are our centers: "); 
        System.out.println(centers);
    }
    
    
    /** adds yellow nodes that represent the centers of ea k cluster
     * 
     * @param graph - imported Graph
     * @param k - num of k clusters
     */
    public void add_centers(Graph graph, int k) { 
        System.out.println("We have this many nodes: " + graph.getNodeCount());
        //System.out.println("These are our centers: " + centers);
        //could also do for HashMap of blah and remove k as param
        for (int j = 0; j < k; j++) {
            if (centers.get(j).get("num")>0) { 
                double dcurx = centers.get(j).get("x");
                float curx = (float)dcurx;
                double dcury = centers.get(j).get("y");
                float cury = (float)dcury; 

                Node newCenter = graphModel.factory().newNode("CENTER_" + j);
                newCenter.getNodeData().setX(curx);
                newCenter.getNodeData().setY(cury);
                newCenter.getNodeData().setColor(1,1,0);
                newCenter.getNodeData().setSize(10);
                newCenter.getNodeData().setLabel("CENTER_" + j);
                newCenter.getAttributes().setValue("center", "true");
                System.out.println("This is the newest center: " + newCenter);
                graph.readUnlockAll();
                graph.addNode(newCenter);
                //System.out.println("Cluster "+j+" Center: " + centers.get(j).get("x") + "," 
                        //+ centers.get(j).get("y") + ";\n" + centers.get(j).get("num") + " nodes");
            }     
        }
        System.out.println("Now we have this many nodes: " + graph.getNodeCount());
    }
    
    
    /** removes the centers drawn by add_centers
     * 
     * @param graph - the graph
     */
    public void remove_centers(Graph graph) {
        
        for (Node n : graph.getNodes()) { 
            graph.readUnlockAll();
            graph.removeNode(n);
            /*
            if ((boolean)n.getAttributes().getValue("center")) { 
                System.out.println("Nodes that are centers: " + n);
                graph.removeNode(n);
            }
            */
        }
        
        
        /*
        for (Node n : graph.getNodes()) { 
            if (n.getAttributes().getValue("cluster")==null) {
                System.out.println("the node n is: " + n );
                System.out.println("the node id is: " + n.getId());
                int nid = n.getId(); 
                System.out.println("the node w/ this id is: " + graph.getNode(nid)); 
                //graph.removeNode(graph.getNode(nid));
            }
        }
        */
        
        /*
        //add everything to an ArrayList first b/c the graph locks otherwise
        ArrayList<Node> remove = new ArrayList();
        for (Node n : graph.getNodes()) { 
            String name = (String)n.getAttributes().getValue("id");
            if (name.contains("CENTER")) { 
                remove.add(n);
            }
        }
        for (Node n : remove) { 
            graph.removeNode(n);
        }
        */
        
    }
    
    public void remove_outliers(Graph graph) {
        ArrayList distances = new ArrayList(); 
        for (Node n : graph.getNodes()) { 
            try { 
                double dist = (double)n.getAttributes().getValue("dist_from_cent"); 
                distances.add(dist);
            } catch (Exception e) { 
                //this only happens for centers, so we'll just catch and ignore
            }
        }
        Collections.sort(distances); 
        //just gonna count the median as the middle value (could round 
        //if there is an odd num, but our data's so big that it shouldn't matter)
        double min = (double)distances.get(0);
        double median = (double)distances.get(graph.getNodeCount()/2);
        double max = (double)distances.get(graph.getNodeCount()-1);
        double upNum = graph.getNodeCount()*0.95;
        double upper = (double)distances.get((int)upNum);
        System.out.println("This is our min: " + min);
        System.out.println("This is our median: " + median);
        System.out.println("This is the upper cut off: " + upper);
        System.out.println("This is our max: " + max);
        System.out.println("These are all of our values: ");
        
        ArrayList<Node> remove = new ArrayList(); 
        for (Node n : graph.getNodes()) { 
            double dist = (double)n.getAttributes().getValue("dist_from_cent");
            if (dist>upper) { 
                remove.add(n);
            }
        }
        for (Node n : remove) { 
            graph.removeNode(n);
        }
        
        /*
        for (Object d : distances) { 
            System.out.println(d);
        }
        */
        
        /*
        for (Node n : graph.getNodes()) { 
                double xco = n.getNodeData().x();
                double yco = n.getNodeData().y();
                int cluster = (int) n.getAttributes().getValue("cluster");
                HashMap<String,Double> oldCenter = centers.get(cluster);
                double oldNum = oldCenter.get("num");
                double oldX = oldCenter.get("x"); 
                
                double distance = 0.0; 
                System.out.println("cluster: " + cluster + ", x: " + xco + ", y: " + yco);
                //n.getAttributes().setValue("dist_cent", distance);
        }
        */
    }
    
    
    /**
     * Problem w/ how we calc polarity: if 3 completely polarized groups
     * exist and k=2, it is likely that 2 groups will completely belong to 1 
     * k cluster, and there will be no outEdges, resulting in a polarity of 1.0
     * this is also true if k=3 (the appropriate num of k)
     * in this situation, we don't know which is better k value
     * 
     * @param graph
     * @param k 
     */
    /*
    public double calc_polarity(Graph graph, int k, String outputFile) {
        double graphPolarity = 0;
        double inEdges = 0;
        double outEdges = 0;
        
        AttributeController ac = Lookup.getDefault().lookup(AttributeController.class);
        AttributeModel model = ac.getModel();
        AttributeColumn sourceCol = model.getNodeTable().getColumn("cluster");
        
        for (Node n : graph.getNodes()) {
            Object mod = n.getAttributes().getValue(sourceCol.getIndex());
            for (Node neighbor : graph.getNeighbors(n)) {
                double weight = 1.0;
                if (neighbor.getAttributes().getValue(sourceCol.getIndex()).equals(mod)) 
                    inEdges+=weight;
                else 
                    outEdges+=weight;
            }
        }
        System.out.println("inEdges: " + inEdges); 
        System.out.println("outEdges: " + outEdges);
        double totalEdges = inEdges + outEdges;
        System.out.println("totalEdges: " + totalEdges); 
        graphPolarity = 1-(outEdges/totalEdges);
        System.out.println("polarity as 1-(edges without)/(total edges): " + graphPolarity);        
        try { 
            PrintWriter out = new PrintWriter(outputFile);
            out.println(graphPolarity);
        } catch (Exception e) { 
            System.out.println("Error printing graph polarity");
        }
        return graphPolarity;
        
    }
    */
    //type is either "cluster" or "Modularity Class"
    public double calc_polarity(Graph graph, String type) {
        double graphPolarity = 0;
        double inEdges = 0;
        double outEdges = 0;
        int groups = 0; 
        
        AttributeController ac = Lookup.getDefault().lookup(AttributeController.class);
        AttributeModel model = ac.getModel();
        AttributeColumn sourceCol = model.getNodeTable().getColumn(type);
        
        for (Node n : graph.getNodes()) {
            Object mod = n.getAttributes().getValue(sourceCol.getIndex()); 
            try { 
                if ((int)mod>groups)
                    groups=(int)mod;
                for (Node neighbor : graph.getNeighbors(n)) {
                    double weight = 1.0;
                    if (neighbor.getAttributes().getValue(sourceCol.getIndex()).equals(mod)) 
                        inEdges+=weight;
                    else 
                        outEdges+=weight;
                }
            } catch (Exception e) { 
                System.out.println(e);
            }
        }
        System.out.println("number of groups: " + (groups+1));
        System.out.println("inEdges: " + inEdges); 
        System.out.println("outEdgest: " + outEdges);
        double totalEdges = inEdges + outEdges;
        System.out.println("totalEdges: " + totalEdges); 
        graphPolarity = inEdges/totalEdges;
        System.out.println("polarity as (in edges)/(total edges): " + graphPolarity);        
        
        return graphPolarity;
        
    }
    
    public double calc_polarity(Graph graph) {
        double graphPolarity = 0;
        double inEdges = 0;
        double outEdges = 0;
        int groups = 0; 
        
        AttributeController ac = Lookup.getDefault().lookup(AttributeController.class);
        AttributeModel model = ac.getModel();
        AttributeColumn sourceCol = model.getNodeTable().getColumn("cluster");
        
        for (Node n : graph.getNodes()) {
            Object mod = n.getAttributes().getValue(sourceCol.getIndex()); 
            if ((int)mod>groups)
                groups=(int)mod;
            try { 
                for (Node neighbor : graph.getNeighbors(n)) {
                    double weight = 1.0;
                    if (neighbor.getAttributes().getValue(sourceCol.getIndex()).equals(mod)) 
                        inEdges+=weight;
                    else 
                        outEdges+=weight;
                }
            } catch (Exception e) { 
                System.out.println(e);
            }
        }
        System.out.println("number of groups: " + (groups+1));
        System.out.println("inEdges: " + inEdges); 
        System.out.println("outEdgest: " + outEdges);
        double totalEdges = inEdges + outEdges;
        System.out.println("totalEdges: " + totalEdges); 
        graphPolarity = inEdges/totalEdges;
        System.out.println("polarity as (in edges)/(total edges): " + graphPolarity);        
        
        return graphPolarity;
        
    }
    
    /** exports current workspace as gexf file
     * 
     * @param outputFile - output file name (include .gexf)
     */
    public void export_gexf(String outputFile) { 
                // Export the graphs to the desired formats    
        // first, export a .gexf graph, which can be loaded into the Gephi interactive
        // platform, and also used to create sigma.js graphs for webpages
        System.out.println("Creating " + outputFile);
        GraphExporter exporter = (GraphExporter) ec.getExporter("gexf");     //Get GEXF exporter
        exporter.setExportVisible(true);  //Only exports the visible (filtered) graph
        exporter.setWorkspace(workspace);
        try {
            ec.exportFile(new File(outputFile), exporter);
            System.out.println("we exported!");
        } catch (IOException ex) {
            return;
        }
    }
    
    /** export current workspace as png file
     * 
     * @param outputFile - output file name (include ".png")
     */
    public void export_png(String outputFile) { 
        pngExporter.setHeight(2000);
        pngExporter.setWidth(2000);
        pngExporter.setMargin(10);
        pngExporter.setTransparentBackground(true);
        pngExporter.setWorkspace(workspace);
        ec.exportStream(baos, pngExporter);
        byte[] png = baos.toByteArray();
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);            
            fos.write(png);
            fos.close();
        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }    
    }
    
    /** cleans graph so that the nodes are the same size and so that
     * there are no edges (this makes it easier to look at for polarity)
     * @param g - the graph
     */
    public void clean_edges(Graph graph) { 
        //uniform size
        for (Node n : graph.getNodes()) 
            n.getNodeData().setSize(20);
        //gets rid of edges
       graph.clearEdges();
    }
    
    public void square_edges(Graph graph) { 
        for (Edge e : graph.getEdges()) { 
            float weight = e.getWeight();
            double sqr = Math.pow(weight,2);
            e.setWeight((float)sqr);
        }
    }
    
    public void filter_core(Graph graph) { 
        ArrayList weights = new ArrayList(); 
        for (Edge e : graph.getEdges()) { 
            weights.add(e.getWeight());
        }
        Collections.sort(weights); 
        double upNum = graph.getEdgeCount()*0.97; 
        float fupper = (float)weights.get((int)upNum);
        double upper = (double)fupper;
        ArrayList<Edge> removeEdges = new ArrayList(); 
        for (Edge e : graph.getEdges()) { 
            double weight = (double)e.getWeight();
            if (weight<upper) { 
                removeEdges.add(e);
            }
        }
        for (Edge e : removeEdges) { 
            graph.removeEdge(e);
        }
        
        ArrayList<Node> removeNodes = new ArrayList(); 
        for (Node n : graph.getNodes()) { 
            int degree = graph.getDegree(n); 
            if (degree<2) { 
                removeNodes.add(n);
            }
        }
        for (Node n : removeNodes) { 
            graph.removeNode(n);
        }
        
    }
    
    public void run_mod(Graph graph) { 
        Modularity modularity = new Modularity();
        modularity.setResolution(1.00);
        modularity.execute(graphModel, attributeModel);
        // partition nodes (color them) by modularity group        
        Partition p = partitionController.buildPartition(attributeModel.getNodeTable().getColumn("Modularity Class"), graph);
        NodeColorTransformer nodeColorTransformer = new NodeColorTransformer();
        nodeColorTransformer.randomizeColors(p);
        Iterator<Color> colors = nodeColorTransformer.getMap().values().iterator();
        partitionController.transform(p, nodeColorTransformer);
        
        //assign attrs
    }
    
    
    
    
    public static void main(String[] args) {
        GraphPolarity gp = new GraphPolarity(); 
        Graph g = gp.import_csv("mincut/gaza_clean.gexf",false);
        //Graph g = gp.import_gexf("mincut/clean.gexf");
        gp.prep_graph(g);
        gp.run_mod(g);
        
        int k=3; 
        gp.prep_kmeans(g, k);
        gp.run_kmeans(g, k);
        
        double polarity = gp.calc_polarity(g, "cluster");
        System.out.println("this is polarity: " + polarity);
        
        gp.add_centers(g, k);
                
        gp.export_png("mincut/clean_" + polarity + ".png");
        gp.export_gexf("mincut/clean_" + polarity + ".gexf");
        
        /*
        int k=2;
        gp.prep_kmeans(g, k);
        gp.run_kmeans(g, k);
        gp.add_centers(g, k);
        
        //double polarity = gp.calc_polarity(g, "Modularity Class");
        double polarity = gp.calc_polarity(g, "cluster");
        System.out.println("this is polarity: " + polarity);
        //gp.export_gexf("mod/german_mod_" + polarity + ".gexf");
        //gp.export_png("mod/masen_mod_" + polarity + ".png");
        
        gp.export_gexf("mincut/gaza2_" + polarity + ".gexf");
        gp.export_png("mincut/gaza2_" + polarity + ".png");
        */
        
        /*
        if (args.length == 10) {
            try {
                String csvFile = args[0]; //co-retweeted.csv
                String outputPath = args[1]; //name/images/
                String outputName = args[2]; //co-retweeted
                Boolean edgeDirection = args[3].equals("directed"); //directed = true
                Boolean giantComponent = args[4].equals("true");   //giant component = true          
                Boolean modularity = args[5].equals("true");
                int degreeFilter = Integer.parseInt(args[6]);
                String labels = args[7];
                int pngOutputSize = Integer.parseInt(args[8]);
                String dir = args[10];

                //initial: run the cleaning, then export
                GraphPolarity h = new GraphPolarity();
                String inputFile = dir + "co-retweeted";
                Graph initGraph = h.import_csv(inputFile + ".csv", edgeDirection);
                //Graph initGraph = h.import_gexf(inputFile + ".gexf"); 
                h.prep_graph(initGraph); 
                h.export_gexf(inputFile + "_clean.gexf");
                //run kmeans on the export
                //would be great if we didn't have to create a new GC_KM ea time
                for (int i=2; i<7; i++) { 
                    GraphPolarity j = new GraphPolarity();
                    String cleanFile = dir + "co-retweeted_clean";
                    Graph graph = j.import_gexf(cleanFile + ".gexf");             
                    int k=i; 
                    String outputFile = dir + "filtered" + k; 
                    j.prep_kmeans(graph, k); 
                    j.run_kmeans(graph, k);
                    double polarity = j.calc_polarity(graph, k); 
                    j.export_gexf(outputFile + "_" + polarity + ".gexf");
                    j.add_centers(graph, k); 
                    j.export_png(outputFile +"_" + polarity + ".png");
                }
                
                
                
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        } else {
            System.out.println(args.length);
            for (int i = 0; i < args.length; i++) {
                System.out.println(args[i]);
            }
            
        }
        */
                
    }
}

