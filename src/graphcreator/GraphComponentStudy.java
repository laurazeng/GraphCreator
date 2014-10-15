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
import java.util.Iterator;
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
public class GraphComponentStudy {
       public void script(String inputFile, String outputPath, String outputName,
            Boolean directed, Boolean giantComponent, int degreeFilter,
            String labels, int pngOutputSize) throws IOException {
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
        
        // report file
        PrintWriter out = new PrintWriter(outputPath + outputName + "_component_report.html");
        out.write("<html>\n <head>\n\t<title>" + outputName + "Component Report </title>\n</head>\n\n<body>");
        out.write("<h1>" + outputName + "Component Report</h1>\n");
        
        //Import file
        System.out.println("Importing graph from  " + inputFile);
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
            return;
        }

        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);        
        System.out.println("Graph imported successfully");
        
        
        // Filtering
        // number of edges and nodes prefilter
        Graph preFilter = graphModel.getGraphVisible();
        int preNodes = preFilter.getNodeCount();
        int preEdges = preFilter.getEdgeCount();
        
        // instaniate the final graph filter and view 
        Query query;
        GraphView view;
        
        // filter out anything that doesn't have degree gt inputed degreeFilter
        DegreeRangeBuilder.DegreeRangeFilter degreeRangeFilter = new DegreeRangeBuilder.DegreeRangeFilter();
        degreeRangeFilter.init(graphModel.getGraph());
        degreeRangeFilter.setRange(new Range(1, Integer.MAX_VALUE));     //Remove nodes with degree < 1
        Query degreeQuery = filterController.createQuery(degreeRangeFilter);
        view = filterController.filter(degreeQuery);
        query = degreeQuery;

        GiantComponentBuilder.GiantComponentFilter giantComp = 
                new GiantComponentBuilder.GiantComponentFilter();
        giantComp.init(graphModel.getGraph());
        
        // set visible to the filtered nodes        
        graphModel.setVisibleView(view);
        Graph g = graphModel.getGraphVisible();
        
        // report the number of nodes and edges that were filtered
        int nodeCount = g.getNodeCount();
        int edgeCount = g.getEdgeCount();
        System.out.println("Resulting filtered graph has:");
        System.out.println("Nodes: " + nodeCount + " (" + (100.0*nodeCount)/(1.0*preNodes) + "%)");
        System.out.println("Edges: " + edgeCount + " (" + (100.0*nodeCount)/(1.0*preEdges) + "%)");
        System.out.println();
        
        out.write("<p><strong>Resulting filtered graph has</strong>:<br />\n");
        out.write("Nodes: " + nodeCount + " (" + (100.0*nodeCount)/(1.0*preNodes) + "%)<br />\n");
        out.write("Edges: " + edgeCount + " (" + (100.0*nodeCount)/(1.0*preEdges) + "%)</p>\n\n");
        
 
        // Modularity Statistics
        System.out.println("Running modularity algorithm");
        Modularity modularity = new Modularity();
        modularity.setResolution(1.00);
        modularity.execute(graphModel, attributeModel);
       
                
        Graph graph = graphModel.getGraph();
        
        // partition nodes (color them) by modularity group        
        Partition p = partitionController.buildPartition(attributeModel.getNodeTable().getColumn("Modularity Class"), graph);
        NodeColorTransformer nodeColorTransformer = new NodeColorTransformer();
        nodeColorTransformer.randomizeColors(p);
        Iterator<Color> colors = nodeColorTransformer.getMap().values().iterator();
        partitionController.transform(p, nodeColorTransformer);
        
        // We will go through each modularity group, find how many nodes are in it
        // and print out the number and the color of the group
        // And then rank the node color in that group by the degree of the nodes
        PartitionBuilder.NodePartitionFilter partitionFilter = new PartitionBuilder.NodePartitionFilter(p);
        Part[] v = p.getParts();
        System.out.println("There are " + v.length + " modularity groups");
        System.out.println("Reporting the size of the groups as %s of filtered nodes");
        
        out.write("<h1>Modularity Report</h1>\n");
        out.write("<p>There are <strong>" + v.length + "</strong> modularity classes<br />\n");
        out.write("<em>Reporting the size of the groups as percentages of filtered nodes</em><p>\n");
        for (int i = 0; i < v.length; i++) {
            // add a subfilter to filter individual modularity groups
            int part = Integer.parseInt(v[i].getDisplayName());
            partitionFilter.unselectAll();
            partitionFilter.addPart(p.getPartFromValue(part));
            Query query2 = filterController.createQuery(partitionFilter);            
            INTERSECTIONBuilder.IntersectionOperator intersectionOperator = new INTERSECTIONBuilder.IntersectionOperator();            
            Query query3 = filterController.createQuery(intersectionOperator);
            filterController.setSubQuery(query3, query);
            filterController.setSubQuery(query3, query2);
            GraphView view3 = filterController.filter(query3);
            graphModel.setVisibleView(view3);            
            Graph graphPart = graphModel.getGraphVisible();
            
            System.out.println("Group " + part);
            System.out.println("Nodes: " + graphPart.getNodeCount() + " (" +
                    String.format("%.2f",(100.0*graphPart.getNodeCount())/(1.0*nodeCount)) + "%)");
            
            

            // rank color gradient by degree
            Color c1 = colors.next();
            System.out.println("Color " + c1);
            
            String rgb = Integer.toHexString(c1.getRGB());
            rgb = rgb.substring(2, rgb.length());
            
            out.write("<p><strong>Group " + part + "</strong><br>\n");
            out.write("Nodes: " + graphPart.getNodeCount() + " (" + 
                    String.format("%.2f",(100.0*graphPart.getNodeCount())/(1.0*nodeCount)) + "%) <br />\n");
            out.write("<img src='http://dummyimage.com/75/" + rgb + "/" + rgb + "&text=%20' /></p>\n");
            
            Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
            AbstractColorTransformer colorTransformer = 
                    (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
            // to make the ranking, create a gradient with the colors, made darker and lighter
            colorTransformer.setColors(new Color[]{c1.brighter().brighter(),c1, c1.darker().darker()});
            rankingController.transform(degreeRanking,colorTransformer);
            
        }
        // reset the graph to the filter before the modularity classes
        graphModel.setVisibleView(view); 
        
        
        // rank nodes by degree
        System.out.println("Ranking nodes by degree");
        Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
        AbstractSizeTransformer sizeTransformer = 
                (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(5);
        sizeTransformer.setMaxSize(60);
        rankingController.transform(degreeRanking,sizeTransformer);
        
        // Force Atlas layout
        System.out.println("Laying out the graph");
        
        int iterations = 0;
        // right now just guessing at how many iterations we need
        // need to find some way to test this
        if (nodeCount < 2000)
            iterations = 5000;
        else if (nodeCount < 5000)
            iterations = 10000;
        else if (nodeCount < 10000)
            iterations = 15000;
        else if (nodeCount < 50000)
            iterations = 30000;
        else
            iterations = 50000;
        
        ForceAtlas2 f2 = new ForceAtlas2Builder().buildLayout();
        f2.setJitterTolerance(.05);
        f2.setGraphModel(graphModel);
        f2.initAlgo();
        for (int i = 0; i < iterations && f2.canAlgo(); i++) {
            f2.goAlgo();
//            if (i%1000 == 0 && i != 0) {
//                System.out.println(i + " iterations completed");
//                // Intermediate Layout Previews
//                PreviewController previewController = Lookup.getDefault().lookup(PreviewController.class);
//                PreviewModel previewModel = previewController.getModel();
//                previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 40);
//                previewController.refreshPreview();
//
//                // export to png
//                ExportController ec = Lookup.getDefault().lookup(ExportController.class);
//                try {
//                    ec.exportFile(new File("graph_iteration_" + i + ".png"));
//                } catch (IOException ex) {
//                    ex.printStackTrace();
//                    return;
//                }
//                
//            }
        }
        f2.endAlgo();
        
        // run ForceAtlas2 a second time to fix overlapping nodes
        f2.setAdjustSizes(true);
        f2.initAlgo();
        for (int i = 0; i < 200 && f2.canAlgo(); i++) 
            f2.goAlgo();
        f2.endAlgo();
        
        // Export the graphs to the desired formats
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        
        // Draw graphs of the components
        // create a directory to store the images in
        String compPath = outputPath + outputName + "_components/";
        File compDir = new File(compPath);
        compDir.mkdirs();

        // partition nodes (color them) by modularity group        
        Partition compPartition = partitionController.buildPartition(attributeModel.getNodeTable().getColumn("Component ID"), graph);
         
        // We will go through each component, find how many nodes are in it
        // and print out the number and the color of the group
        // And then rank the node color in that group by the degree of the nodes
        PartitionBuilder.NodePartitionFilter compFilter = new PartitionBuilder.NodePartitionFilter(compPartition);
        Part[] components = compPartition.getParts();
        System.out.println("There are " + components.length + " distinct componenets in the graph");
        System.out.println("Reporting the size of the components as %s of filtered nodes");
        
        out.write("<h1>Component Report</h1>\n");
        out.write("<p>There are <strong>" + components.length + "</strong> components<br />\n");
        out.write("<em>Reporting the size of the groups as percentages of filtered nodes</em><p>\n");
        for (int i = 0; i < components.length; i++) {
            // add a subfilter to filter individual modularity groups
            int part = Integer.parseInt(components[i].getDisplayName());
            compFilter.unselectAll();
            compFilter.addPart(compPartition.getPartFromValue(part));
            Query query2 = filterController.createQuery(compFilter);
            GraphView view3 = filterController.filter(query2);
            graphModel.setVisibleView(view3);            
            Graph graphPart = graphModel.getGraphVisible();
            
            
            
            System.out.println("Component " + part);
            System.out.println("Nodes: " + graphPart.getNodeCount() + " (" +
                    String.format("%.2f",(100.0*graphPart.getNodeCount())/(1.0*nodeCount)) + "%)");
            
            out.write("<p><strong>Component " + part + "</strong><br>\n");
            out.write("Nodes: " + graphPart.getNodeCount() + " (" + 
                    String.format("%.2f",(100.0*graphPart.getNodeCount())/(1.0*nodeCount)) + "%) </p>\n");
            
            if (graphPart.getNodeCount() > 1) {          
                PreviewModel previewModel = previewController.getModel();
                previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 40);
                previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
                previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.BLACK));
                previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, new Font("Arial", Font.PLAIN,5));
                previewController.refreshPreview();

                // export to png
                PNGExporter pngExporter = (PNGExporter) ec.getExporter("png");
                pngExporter.setHeight(1000);
                pngExporter.setWidth(1000);
                pngExporter.setMargin(10);
                pngExporter.setTransparentBackground(true);
                pngExporter.setWorkspace(workspace);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ec.exportStream(baos, pngExporter);
                byte[] png = baos.toByteArray();
                try {
                    FileOutputStream fos = new FileOutputStream(compPath + "component_" + i + ".png");
                    fos.write(png);
                    fos.close();
                } catch (FileNotFoundException ex) {
                    Exceptions.printStackTrace(ex);
                }           
            }

        }
        graphModel.setVisibleView(view);
        
        // first, export a .gexf graph, which can be loaded into the Gephi interactive
        // platform, and also used to create sigma.js graphs for webpages
        System.out.println("Creating " + outputPath + outputName + ".gexf");
        GraphExporter exporter = (GraphExporter) ec.getExporter("gexf");     //Get GEXF exporter
        exporter.setExportVisible(true);  //Only exports the visible (filtered) graph
        exporter.setWorkspace(workspace);
        try {
            ec.exportFile(new File( outputPath + outputName + ".gexf"), exporter);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
        
        if (labels.equals("both") || labels.equals("no")) {
            // we will make the graph without labels first
            // adjust the preview 
            System.out.println("Creating " + outputPath + outputName + ".png");
            PreviewModel previewModel = previewController.getModel();
            previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 40);
            previewModel.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT, true);            
            previewModel.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, 5);
            previewController.refreshPreview();

            // export to png
            
            PNGExporter pngExporter = (PNGExporter) ec.getExporter("png");
            pngExporter.setHeight(pngOutputSize);
            pngExporter.setWidth(pngOutputSize);
            pngExporter.setMargin(10);
            pngExporter.setTransparentBackground(true);
            pngExporter.setWorkspace(workspace);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ec.exportStream(baos, pngExporter);
            byte[] png = baos.toByteArray();

            try {
                FileOutputStream fos = new FileOutputStream(outputPath + outputName + ".png");
                fos.write(png);
                fos.close();
            } catch (FileNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            }
            
        } 
        
        if (labels.equals("both") || labels.equals("yes")) {
            // if we want to create a graph with labels, we will create it now
            // first, make the nodes larger, to allow for the label size
            sizeTransformer.setMinSize(9);
            sizeTransformer.setMaxSize(100);
            rankingController.transform(degreeRanking,sizeTransformer);
            
            // now rerun the ForceAtlas2 layout to adjust for size
            f2.setAdjustSizes(true);
            f2.initAlgo();
            for (int i = 0; i < 500 && f2.canAlgo(); i++) 
                f2.goAlgo();
            f2.endAlgo();
            
            // and make the nodes smaller again
            sizeTransformer.setMinSize(5);
            sizeTransformer.setMaxSize(60);
            rankingController.transform(degreeRanking,sizeTransformer);
            
            // adjust the preview 
            System.out.println("Creating " + outputPath + outputName + "_labels.png");
            PreviewModel previewModel = previewController.getModel();
            previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 40);                
            previewModel.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT, true);            
            previewModel.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, 5);
            previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
            previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.BLACK));
            previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, new Font("Arial", Font.PLAIN,5));
            previewController.refreshPreview();

            // export to png
            PNGExporter pngExporter = (PNGExporter) ec.getExporter("png");
            pngExporter.setHeight(pngOutputSize);
            pngExporter.setWidth(pngOutputSize);
            pngExporter.setMargin(10);
            pngExporter.setTransparentBackground(true);
            pngExporter.setWorkspace(workspace);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ec.exportStream(baos, pngExporter);
            byte[] png = baos.toByteArray();

            try {
                FileOutputStream fos = new FileOutputStream(outputPath + outputName + "_labels.png");
                fos.write(png);
                fos.close();
            } catch (FileNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            }       
        }
        
        out.close();
       
    }

    
}
