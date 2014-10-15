/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphcreator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.filters.api.FilterController;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.partition.api.Partition;
import org.gephi.partition.api.PartitionController;
import org.gephi.partition.plugin.NodeColorTransformer;
import org.gephi.preview.api.PreviewController;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.RankingController;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;


/**
 *
 * @author Samantha and Laura
 */
public class GraphPolarity_wip {
    
    
    /**
     * Given a .gexf file, we will find the center, create an image with the 
     * center marked as a red node and other nodes in gradient by their polarity,
     * and create a csv file with the polarity of all nodes as the normalized
     * distance to the center (normalized by dividing by distance to farthest node)
     */
    public void script(String inputFile,String outputFile,int k,int nodeSize) throws FileNotFoundException {
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        //Get controllers and models
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);        
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();        
        RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);
        PartitionController partitionController = Lookup.getDefault().lookup(PartitionController.class);        
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        PreviewController previewController = Lookup.getDefault().lookup(PreviewController.class);
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        
        //Import file
        System.out.println("Importing graph from  " + inputFile);
        Container container;
        try {
            File file = new File(inputFile);
            container = importController.importFile(file);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);        
        
        
        
        // FINDING CENTER WITH K-MEANS
        // where the number of clusters is k (input into script)
        Graph graph = graphModel.getGraph();
        
        // set k as 2
        //int k = 2;        
        // SEED: randomly assign each node to a cluster (k clusters)
        Random r = new Random(); 
        for (Node n : graph.getNodes()) {
            //assign clusters randomly
            n.getAttributes().setValue("cluster", r.nextInt(k));
            //System.out.println(n.getAttributes().getValue("cluster"));
        }
        
        // iterate       
        int iterations = 500;
        HashMap<Integer,HashMap<String,Double>> centers = new HashMap<Integer,HashMap<String,Double>>();
        //for all groups k, find the mean center of all the points and store these centers in centers
        //set up a coor w/ all keys, but no values
        for (int i=0; i<k; i++)
            centers.put(i,null);
        //System.out.println(centers);
       
        for (int i=0; i<iterations; i++) { 
            //calculate the mean center for the clusters
            for (Node n : graph.getNodes()) {
                int cluster = (int) n.getAttributes().getValue("cluster");
                double xco = n.getNodeData().x();
                double yco = n.getNodeData().y();
                //if this is our first time adding, then we just want to add
                //the 
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
            }
            //System.out.println(centers);
            //System.out.println(centers.get(0));

            //reset the num of ea cluster to 0 b/c we're reassigning all clusters
            for (int j=0; j<k; j++) { 
                centers.get(j).put("num",0.0);
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
                        //also, we should change the cluster value to reflect this
                        n.getAttributes().setValue("cluster", j);
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
        NodeColorTransformer nodeColorTransformer = new NodeColorTransformer();
        nodeColorTransformer.randomizeColors(p);
        Iterator<Color> colors = nodeColorTransformer.getMap().values().iterator();
        partitionController.transform(p, nodeColorTransformer);


        System.out.println("After everything, here are our centers: "); 
        System.out.println(centers);
            

        //****** POLARITY STUFF BELOW ******/
        
        /*
        float centerX = 0;
        float centerY = 0;
        */
        //double ptX = Math.abs(center1X - center0X) * .5 * (Math.pow(Math.min(size1, size0),.5)/Math.pow(Math.max(size1,size0),.5));
//        double ptX = Math.abs(center1X - center0X) * (Math.pow(size1,.5)/(Math.pow(size1,.5) + Math.pow(size0,.5)));
//        //System.out.println(ptX);
//        if (center0X > center1X) 
//            centerX = (float) (center0X - ptX);
//        else
//            centerX = (float) (center0X + ptX);
//        //System.out.println(centerX);
//        
//        //double ptY = Math.abs(center1Y - center0Y) * .5 * (Math.pow(Math.min(size1, size0),.5)/Math.pow(Math.max(size1, size0),.5));
//        double ptY = Math.abs(center1Y - center0Y) * (Math.pow(size1,.5)/(Math.pow(size1,.5) + Math.pow(size0,.5)));
//        //System.out.println(ptY);
//        if (center0Y > center1Y) 
//            centerY = (float) (center0Y - ptY);
//        else
//            centerY = (float) (center0Y + ptY);
        //System.out.println(centerY);
        /*
        double graphPolarity = 0;
        double inEdges = 0;
        double outEdges = 0;
        
        AttributeController ac = Lookup.getDefault().lookup(AttributeController.class);
        AttributeModel model = ac.getModel();
        AttributeColumn sourceCol = model.getNodeTable().getColumn("cluster");
        
       
        double maxPolarity = 0;
        for (Node n : graph.getNodes()) {
            NodeData data = n.getNodeData(); 
            double polarity = Math.sqrt(Math.pow((centerY - data.y()), 2) + Math.pow((centerX - data.x()), 2));
            maxPolarity = Math.max(polarity, maxPolarity);
            
            Object mod = n.getAttributes().getValue(sourceCol.getIndex());
            for (Node neighbor : graph.getNeighbors(n)) {
                float weight = 1;
//                try {
//                    weight = graph.getEdge(n, neighbor).getWeight();
//                } catch (NullPointerException e) {
//                    weight = 1;
//                }

                
                if (neighbor.getAttributes().getValue(sourceCol.getIndex()).equals(mod)) 
                    inEdges = inEdges + weight;
                else 
                    outEdges = outEdges + weight;
            }
        }
        
        double totalEdges = inEdges + outEdges;
        graphPolarity = (inEdges/totalEdges);
        //double graphPolarity2 =  ((k * inEdges/totalEdges) - (outEdges/totalEdges))/k;
        System.out.println("polarity as (edges within)/(total edges): " + graphPolarity);        
        //System.out.println("polarity as (edges within)/(total edges) - 1/k: " + graphPolarity2);
        System.out.println();
        
        PrintWriter out = new PrintWriter("polarities.csv");
        
        for (Node n : graph.getNodes()) {
            NodeData data = n.getNodeData(); 
            double polarity = Math.sqrt(Math.pow((centerY - data.y()), 2) + Math.pow((centerX - data.x()), 2))/maxPolarity;
            data.getAttributes().setValue("polarity", polarity);
            if ((int)n.getAttributes().getValue("cluster") == 0) {
                polarity = polarity * -1;
            }
            out.write(n.getNodeData().getId() + "," + polarity + "\n");
        }
        out.close();
//        
        Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
        AbstractSizeTransformer sizeTransformer = 
                (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(nodeSize);
        sizeTransformer.setMaxSize(nodeSize);
        rankingController.transform(degreeRanking,sizeTransformer);
        
        Partition p = partitionController.buildPartition(attributeModel.getNodeTable().getColumn("cluster"), graph);
        PartitionBuilder.NodePartitionFilter partitionFilter = new PartitionBuilder.NodePartitionFilter(p);
        Part[] v = p.getParts();
        GraphView all = graphModel.getVisibleView();
     
        for (int i = 0; i < v.length; i++) {
                // add a subfilter to filter individual modularity groups
                int part = Integer.parseInt(v[i].getDisplayName());
                //System.out.println("Group " + part);
                partitionFilter.unselectAll();
                partitionFilter.addPart(p.getPartFromValue(part));
                Query query2 = filterController.createQuery(partitionFilter);            
                GraphView view3 = filterController.filter(query2);
                graphModel.setVisibleView(view3);            
                Graph graphPart = graphModel.getGraphVisible();
                */
                /*
                Color[] colors;
                if (part == 1)
                    colors = new Color[]{new Color(255,255,255),new Color(0,75,150),new Color(0,0,0)};
                else
                    colors = new Color[]{new Color(255,255,255),new Color(225,0,0),new Color(0,0,0)};
//                System.out.println("Color " + c1);
//                System.out.println(c1.brighter().brighter());
                */
        /*
                AttributeColumn polarityColumn = attributeModel.getNodeTable().getColumn("polarity");
                Ranking polarityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, polarityColumn.getId());
                *//*
                AbstractColorTransformer colorTransformer =
                        (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
        
                colorTransformer.setColors(colors);
                rankingController.transform(polarityRanking,colorTransformer);
                */

                
               /* 

            }
        graphModel.setVisibleView(all);
        AttributeColumn polarityColumn = attributeModel.getNodeTable().getColumn("polarity");
        Ranking polarityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, polarityColumn.getId());
        /*
        AbstractColorTransformer colorTransformer =
                (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
        // to make the ranking, create a gradient with the colors, made darker and lighter
        colorTransformer.setColors(new Color[]{new Color(225,225,225),new Color(30,30,30)});
        rankingController.transform(polarityRanking,colorTransformer);
        */
        
        /*
        Node center = graphModel.factory().newNode("CENTER");
        center.getNodeData().setY(centerY);
        center.getNodeData().setX(centerX);
        center.getNodeData().setColor(0,1,0);
        center.getNodeData().setSize(50);
        //System.out.println("Do we still know our centers? \n"); 
        //System.out.println(centers);
        graph.addNode(center);
        //System.out.println("node: " + " x - " + center.getNodeData().x() + ", y - "+ center.getNodeData("y"));
        int x = (int)center.getNodeData().x();
        int y = (int)center.getNodeData().y(); 
        */
        
        //this code adds nodes where the centers are
        /*
        System.out.println("LOOPING!");
        for (int j = 0; j < k; j++) {
            //getting data about the center
            //System.out.println(centers.get(j));
            //System.out.println("node: " + j);
            double dcurx = centers.get(j).get("x");
            float curx = (float)dcurx;
            System.out.println("x - " + curx);
            double dcury = centers.get(j).get("y");
            float cury = (float)dcury; 
            System.out.println("y - " + cury);
            double dcursize = centers.get(j).get("num");
            float cursize = (float)dcursize;
            System.out.println("size - " + cursize + "\n");
            
            
            Node newCenter = graphModel.factory().newNode("CENTER_" + j);
            newCenter.getNodeData().setX(curx);
            newCenter.getNodeData().setY(cury);
            newCenter.getNodeData().setColor(1,1,0);
            newCenter.getNodeData().setSize(5);
            graph.addNode(newCenter);
            //System.out.println("Cluster "+j+" Center: " + center.get("x") + "," + center.get("y") + ";\n" + center.get("size") + " nodes");
        }
        */
        
        
        
                    
        // export to png
        
        PNGExporter pngExporter = (PNGExporter) ec.getExporter("png");
        pngExporter.setHeight(2000);
        pngExporter.setWidth(2000);
        pngExporter.setMargin(10);
        pngExporter.setTransparentBackground(true);
        pngExporter.setWorkspace(workspace);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
                

                
        String outputPath = ""; 
        String outputName = "brazil_yay";
        // Export the graphs to the desired formats    
        // first, export a .gexf graph, which can be loaded into the Gephi interactive
        // platform, and also used to create sigma.js graphs for webpages
        System.out.println("Creating " + outputPath + outputName + ".gexf");
        GraphExporter exporter = (GraphExporter) ec.getExporter("gexf");     //Get GEXF exporter
        exporter.setExportVisible(true);  //Only exports the visible (filtered) graph
        exporter.setWorkspace(workspace);
        try {
            ec.exportFile(new File( outputPath + outputName + ".gexf"), exporter);
            System.out.println("we exported!");
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
        

        
       
    }
    
    public static void main(String[] args) throws FileNotFoundException {
        GraphPolarity_wip j = new GraphPolarity_wip();
        j.script("test/brazil_cort.gexf","brazil_yay.png",6,30);
        System.out.println("done!");
        /*
        System.out.println("first");
        j.script("test/brazil_cort.gexf","brazil_polar2.png",2,10);
        System.out.println("second");
        j.script("test/brazil_cort.gexf","brazil_polar3.png",3,10);
        System.out.println("third");
        j.script("test/brazil_cort.gexf","brazil_polar4.png",4,10);
        System.out.println("fourth");
        j.script("test/brazil_cort.gexf","brazil_polar5.png",5,10);
        System.out.println("done!");
        */
        /*
        //j.script("src/resources/retweet.gexf","oct 16 retweet polarity.png",10);
        j.script("src/resources/corted.gexf","oct 16 co-retweeted polarity.png",2,30);
        j.script("src/resources/12corted.gexf","12 polarity.png",2,30);
        j.script("src/resources/masen2013.gexf","masen2013 polarity.png",2,30);
        j.script("src/resources/doyourjobgop.gexf","doyourjobgop polarity.png",2,30);
        j.script("src/resources/german/top-hts.gexf","  ",5,30);
        j.script("src/resources/german/single-ht.gexf","  ",5,30);
        j.script("src/resources/german/co-rted.gexf","  ",5,30);
        */
    }
    
}
