/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphcreator;

import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder;
import org.gephi.filters.plugin.graph.GiantComponentBuilder;
import org.gephi.filters.plugin.operator.INTERSECTIONBuilder;
import org.gephi.filters.plugin.partition.PartitionBuilder;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.GraphView;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.io.importer.api.Container;
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
import org.gephi.preview.types.DependantOriginalColor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.Ranking;
import org.gephi.ranking.api.RankingController;
import org.gephi.ranking.api.Transformer;
import org.gephi.ranking.plugin.transformer.AbstractColorTransformer;
import org.gephi.ranking.plugin.transformer.AbstractSizeTransformer;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author Samantha
 */
public class GraphPolarity_wip_backup {
    
    
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
        System.out.println("Graph imported successfully");
        


        // FINDING CENTER average of all points
//        Graph graph = graphModel.getGraph();
//        float xSum = 0;
//        float ySum = 0;
//        for (Node n : graph.getNodes()) {
//            NodeData data = n.getNodeData(); 
//            ySum = ySum + data.y();
//            xSum = xSum + data.x();
//            System.out.println("X: " + data.x() + " Y: " +  data.y());
//        }
//        float centerY = ySum/graph.getNodeCount();
//        float centerX = xSum/graph.getNodeCount();
//        System.out.println("X: " + centerX + " Y: " + centerY);
        
        // FINDING CENTER with the average of the center of modules
//        Modularity modularity = new Modularity();
//        modularity.setResolution(5.00);
//        modularity.execute(graphModel, attributeModel);
//       
//                
//        Graph graph = graphModel.getGraph();
//        
//        // partition nodes (color them) by modularity group        
//        Partition p = partitionController.buildPartition(attributeModel.getNodeTable().getColumn("Modularity Class"), graph);
//
//        // We will go through each modularity group and find the center
//        PartitionBuilder.NodePartitionFilter partitionFilter = new PartitionBuilder.NodePartitionFilter(p);
//        Part[] v = p.getParts();
//        System.out.println("There are " + v.length + " modularity groups");
//        
//        float xSum = 0;
//        float ySum = 0;
//        GraphView all = graphModel.getVisibleView();
//
//        for (int i = 0; i < v.length; i++) {
//            // add a subfilter to filter individual modularity groups
//            int part = Integer.parseInt(v[i].getDisplayName());
//            partitionFilter.unselectAll();
//            partitionFilter.addPart(p.getPartFromValue(part));
//            Query query = filterController.createQuery(partitionFilter);            
//            GraphView view = filterController.filter(query);
//            graphModel.setVisibleView(view);            
//            Graph module = graphModel.getGraphVisible();
//            
//            // we will ignore any modules that are a single node
//            // (maybe increase this to ignore more very small modules, ie to 4 or 5?)
//            if (module.getNodeCount() > 1) {
//            
//                System.out.println("Group " + part);
//                System.out.println("Nodes: " + module.getNodeCount());
//
//                // find center of module
//                float xSumPart = 0;
//                float ySumPart = 0;
//                for (Node n : module.getNodes()) {
//                    NodeData data = n.getNodeData(); 
//                    ySumPart = ySumPart + data.y();
//                    xSumPart = xSumPart + data.x();
//                    }
//                float centerY = ySumPart/module.getNodeCount();
//                float centerX = xSumPart/module.getNodeCount();
//                System.out.println("X: " + centerX + " Y: " + centerY);
//                xSum = centerX + xSum;
//                ySum = centerY + ySum;      
//            }
//       
//            
//        }
//        // reset the graph to the filter before the modularity classes
//        graphModel.setVisibleView(all);
//        float centerY = ySum/v.length;
//        float centerX = xSum/v.length;
//        System.out.println("X: " + centerX + " Y: " + centerY);
        
        // FINDING CENTER WITH K-MEANS
        // where the number of clusters is k (input into script)
        Graph graph = graphModel.getGraph();
        
        // set k as 2
        //int k = 2;        
        Random r = new Random();
        
        
        // SEED: randomly assign each node to a cluster (k clusters)
        for (Node n : graph.getNodes()) {
            n.getAttributes().setValue("cluster", r.nextInt(k));
        }
        
        // iterate       
        int iterations = 50;
        
        
        HashMap <Integer,HashMap <String, Double>> centers = new HashMap<Integer,HashMap <String, Double>>();
        for (int i = 0; i<iterations; i++) {
            for (int j = 0; j < k; j++) {
                HashMap<String,Double> center = new HashMap<String,Double>();
                center.put("x",0.0);
                center.put("y",0.0);
                center.put("size",0.0);
                centers.put(j,center);
            }
            // calculate the new centers
            for (Node n: graph.getNodes()) {
                //find which cluster this node is in
                int cluster = (int) n.getAttributes().getValue("cluster");
                
                // update information about that cluster's center
                HashMap<String,Double> center = centers.get(cluster);
                center.put("x",center.get("x") + n.getNodeData().x());
                center.put("y",center.get("y") + n.getNodeData().y());
                center.put("size",center.get("size")+ 1);
                centers.put(cluster, center);

            }
            
            // calculate the center based on the average of the nodes in the cluster
            for (int j = 0; j < k; j++) {
                HashMap<String,Double> center = centers.get(j);
                center.put("x",center.get("x")/center.get("size"));
                center.put("y",center.get("x")/center.get("size"));
                centers.put(j,center);
                //System.out.println("Cluster "+j+" Center: " + center.get("x") + "," + center.get("y") + "; " + center.get("size") + " nodes");
            }

            
            // reassign clusters based on which center closer to
            for (Node n: graph.getNodes()) {
                int minCenter = 0;
                double minDist = Double.MAX_VALUE;
                
                for (int j = 0; j < k; j++) {
                    HashMap<String,Double> center = centers.get(j);
                    double dist = Math.sqrt(Math.pow((center.get("x") - n.getNodeData().x()), 2) + Math.pow((center.get("y") - n.getNodeData().y()), 2));
                    // update if this is the minimum distance
                    if (dist < minDist) {
                        minDist = dist;
                        minCenter = j;
                    }
                        
                }
                
                // assign node to the nearest cluster
                n.getAttributes().setValue("cluster",minCenter);   
            }

            
            
        }
       for (int j = 0; j < k; j++) {
            HashMap<String,Double> center = centers.get(j);
            //System.out.println("Cluster "+j+" Center: " + center.get("x") + "," + center.get("y") + ";\n" + center.get("size") + " nodes");
        }

        float centerX = 0;
        float centerY = 0;
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
        
        double graphPolarity = 0;
        double inEdges = 0;
        double outEdges = 0;
        
        AttributeController ac = Lookup.getDefault().lookup(AttributeController.class);
        AttributeModel model = ac.getModel();
        AttributeColumn sourceCol = model.getNodeTable().getColumn("cluster");
        
       
//        double maxPolarity = 0;
        for (Node n : graph.getNodes()) {
//            NodeData data = n.getNodeData(); 
//            double polarity = Math.sqrt(Math.pow((centerY - data.y()), 2) + Math.pow((centerX - data.x()), 2));
//            maxPolarity = Math.max(polarity, maxPolarity);
            
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
        
//        PrintWriter out = new PrintWriter("polarities.csv");
//        
//        for (Node n : graph.getNodes()) {
//            NodeData data = n.getNodeData(); 
//            double polarity = Math.sqrt(Math.pow((centerY - data.y()), 2) + Math.pow((centerX - data.x()), 2))/maxPolarity;
//            data.getAttributes().setValue("polarity", polarity);
//            if (n.getAttributes().getValue("cluster") == 0)
//                polarity = polarity * -1;
//            out.write(n.getNodeData().getId() + "," + polarity + "\n");
//        }
//        out.close();
//        
//        Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
//        AbstractSizeTransformer sizeTransformer = 
//                (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
//        sizeTransformer.setMinSize(nodeSize);
//        sizeTransformer.setMaxSize(nodeSize);
//        rankingController.transform(degreeRanking,sizeTransformer);
//        
//        Partition p = partitionController.buildPartition(attributeModel.getNodeTable().getColumn("cluster"), graph);
//        PartitionBuilder.NodePartitionFilter partitionFilter = new PartitionBuilder.NodePartitionFilter(p);
//        Part[] v = p.getParts();
//        GraphView all = graphModel.getVisibleView();
//        
//        for (int i = 0; i < v.length; i++) {
//                // add a subfilter to filter individual modularity groups
//                int part = Integer.parseInt(v[i].getDisplayName());
//                //System.out.println("Group " + part);
//                partitionFilter.unselectAll();
//                partitionFilter.addPart(p.getPartFromValue(part));
//                Query query2 = filterController.createQuery(partitionFilter);            
//                GraphView view3 = filterController.filter(query2);
//                graphModel.setVisibleView(view3);            
//                Graph graphPart = graphModel.getGraphVisible();
//                
//                Color[] colors;
//                if (part == 1)
//                    colors = new Color[]{new Color(255,255,255),new Color(0,75,150),new Color(0,0,0)};
//                else
//                    colors = new Color[]{new Color(255,255,255),new Color(225,0,0),new Color(0,0,0)};
////                System.out.println("Color " + c1);
////                System.out.println(c1.brighter().brighter());
//                
//                AttributeColumn polarityColumn = attributeModel.getNodeTable().getColumn("polarity");
//                Ranking polarityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, polarityColumn.getId());
//                AbstractColorTransformer colorTransformer =
//                        (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
//        
//                colorTransformer.setColors(colors);
//                rankingController.transform(polarityRanking,colorTransformer);
//                
//
//                
//                
//
//            }
//        graphModel.setVisibleView(all);
//        AttributeColumn polarityColumn = attributeModel.getNodeTable().getColumn("polarity");
//        Ranking polarityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, polarityColumn.getId());
//        AbstractColorTransformer colorTransformer =
//                (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
//        // to make the ranking, create a gradient with the colors, made darker and lighter
//        colorTransformer.setColors(new Color[]{new Color(225,225,225),new Color(30,30,30)});
//        rankingController.transform(polarityRanking,colorTransformer);
        
//        Node center = graphModel.factory().newNode("CENTER");
//        center.getNodeData().setY(centerY);
//        center.getNodeData().setX(centerX);
//        center.getNodeData().setColor(0,1,0);
//        center.getNodeData().setSize(50);
//        graph.addNode(center);
//        
//        Node center0 = graphModel.factory().newNode("CENTER 0");
//        center0.getNodeData().setY((float) center0Y);
//        center0.getNodeData().setX((float) center0X);
//        center0.getNodeData().setColor(1,0,0);
//        center0.getNodeData().setSize(50);
//        graph.addNode(center0);
//        
//        Node center1 = graphModel.factory().newNode("CENTER 1");
//        center1.getNodeData().setY((float) center1Y);
//        center1.getNodeData().setX((float) center1X);
//        center1.getNodeData().setColor(0,0,1);
//        center1.getNodeData().setSize(50);
//        graph.addNode(center1);
                    
        // export to png
//        PNGExporter pngExporter = (PNGExporter) ec.getExporter("png");
//        pngExporter.setHeight(2000);
//        pngExporter.setWidth(2000);
//        pngExporter.setMargin(10);
//        pngExporter.setTransparentBackground(true);
//        pngExporter.setWorkspace(workspace);
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        ec.exportStream(baos, pngExporter);
//        byte[] png = baos.toByteArray();
//
//        try {
//            FileOutputStream fos = new FileOutputStream(outputFile);            
//            fos.write(png);
//            fos.close();
//        } catch (FileNotFoundException ex) {
//            Exceptions.printStackTrace(ex);
//        } catch (IOException ex) {
//            Exceptions.printStackTrace(ex);
//        } 
                
        
        
       
    }
    
    public static void main(String[] args) throws FileNotFoundException {
        GraphPolarity_wip_backup j = new GraphPolarity_wip_backup();
        //j.script("src/resources/retweet.gexf","oct 16 retweet polarity.png",10);
        //j.script("src/resources/corted.gexf","oct 16 co-retweeted polarity.png",2,30);
        //j.script("src/resources/12corted.gexf","12 polarity.png",2,30);
        j.script("src/resources/masen2013.gexf","masen2013 polarity.png",2,30);
        /*j.script("src/resources/doyourjobgop.gexf","doyourjobgop polarity.png",2,30);
        j.script("src/resources/german/top-hts.gexf","  ",5,30);
        j.script("src/resources/german/single-ht.gexf","  ",5,30);
        j.script("src/resources/german/co-rted.gexf","  ",5,30);*/
        
    }
    
}
