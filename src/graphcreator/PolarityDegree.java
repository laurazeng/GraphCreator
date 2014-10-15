/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphcreator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.filters.api.FilterController;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.partition.api.PartitionController;
import org.gephi.preview.api.PreviewController;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.RankingController;
import org.openide.util.Lookup;

/**
 *
 * @author Samantha
 */
public class PolarityDegree {
    
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

        Graph graph = graphModel.getGraph();

        double graphPolarity = 0;
        double inEdges = 0;
        double outEdges = 0;
        
        AttributeController ac = Lookup.getDefault().lookup(AttributeController.class);
        AttributeModel model = ac.getModel();
        for (AttributeColumn m : model.getNodeTable().getColumns()) {
            System.out.println(m.getTitle());
        }
        AttributeColumn sourceCol = model.getNodeTable().getColumn("Modularity Class");

        for (Node n : graph.getNodes()) {
            Object mod = n.getAttributes().getValue(sourceCol.getIndex());
            for (Node neighbor : graph.getNeighbors(n)) {
                float weight = 0;
                try {
                    weight = graph.getEdge(n, neighbor).getWeight();
                } catch (NullPointerException e) {
                    weight = 1;
                }
                
                if (neighbor.getAttributes().getValue(sourceCol.getIndex()).equals(mod)) 
                    inEdges = inEdges + weight;
                else 
                    outEdges = outEdges + weight;
            }
        }

            graphPolarity = 1 - (outEdges/inEdges);
            System.out.println("polarity " + graphPolarity);

    }
    
    public static void main(String[] args) throws FileNotFoundException {
        PolarityDegree j = new PolarityDegree();
        //j.script("src/resources/retweet.gexf","oct 16 retweet polarity.png",10);
        //j.script("src/resources/corted.gexf","oct 16 co-retweeted polarity.png",30);
        //j.script("src/resources/12corted.gexf","12 polarity.png",30);
        //j.script("src/resources/masen2013.gexf","masen2013 polarity.png",30);
        j.script("src/resources/doyourjobgop.gexf");
        
    }
}
