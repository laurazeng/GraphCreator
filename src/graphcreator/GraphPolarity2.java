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
public class GraphPolarity2 {
    
    
    /**
     * Given a .gexf file, we will find the center, create an image with the 
     * center marked as a red node and other nodes in gradient by their polarity,
     * and create a csv file with the polarity of all nodes as the normalized
     * distance to the center (normalized by dividing by distance to farthest node)
     */
    public void script(String inputFile) throws FileNotFoundException {
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
        
        AttributeController ac = Lookup.getDefault().lookup(AttributeController.class);
        AttributeModel model = ac.getModel();
        for (AttributeColumn m : model.getNodeTable().getColumns()) {
            System.out.println(m.getTitle());
        }
        AttributeColumn sourceCol = model.getNodeTable().getColumn("modularity class integer default");
        System.out.println(sourceCol);

        
        // FINDING CENTER average of all points
        Graph graph = graphModel.getGraph();
        
       
        
        PrintWriter out = new PrintWriter("polarities.csv");
        for (Node n : graph.getNodes()) {
            double polarity = 0;
            double inEdges = 0;
            double outEdges = 0;
            Object mod = n.getAttributes().getValue(sourceCol.getIndex());
            for (Node neighbor : graph.getNeighbors(n)) {
                 float weight = graph.getEdge(n, neighbor).getWeight();
                
                if (neighbor.getAttributes().getValue(sourceCol.getIndex()).equals(mod)) 
                    inEdges = inEdges + weight;
                else 
                    outEdges = outEdges + weight;
            }
            //System.out.println("in " + inEdges + " out " + outEdges);
            if (inEdges+outEdges != 0) {
                polarity = inEdges/(inEdges+outEdges*outEdges);
                n.getNodeData().getAttributes().setValue("polarity", polarity);
                out.write(n.getNodeData().getId() + "," + polarity + "\n");
            }
            
        }


        out.close();
        
        Ranking degreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
        AbstractSizeTransformer sizeTransformer = 
                (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(30);
        sizeTransformer.setMaxSize(30);
        rankingController.transform(degreeRanking,sizeTransformer);
        
        AttributeColumn polarityColumn = attributeModel.getNodeTable().getColumn("polarity");
        Ranking polarityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, polarityColumn.getId());
        AbstractColorTransformer colorTransformer =
                (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
        // to make the ranking, create a gradient with the colors, made darker and lighter
        colorTransformer.setColors(new Color[]{new Color(225,225,225),new Color(30,30,30)});
        rankingController.transform(polarityRanking,colorTransformer);

                    
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
            FileOutputStream fos = new FileOutputStream("polarity_test.png");            
            fos.write(png);
            fos.close();
        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } 
                
        
        
       
    }
    
    public static void main(String[] args) throws FileNotFoundException {
        GraphPolarity2 j = new GraphPolarity2();
        j.script("src/resources/corted.gexf");
        //j.script("graph.gexf");
        
    }
    
}
