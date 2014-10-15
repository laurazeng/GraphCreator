function drawGraph(graphFile,divId) {

  // Instanciate sigma.js and customize rendering :
  var sigInst = sigma.init(document.getElementById(divId)).drawingProperties({
    defaultLabelColor: '#fff',
    defaultLabelSize: 14,
    defaultLabelBGColor: '#fff',
    defaultLabelHoverColor: '#000',
    labelThreshold: 6,
    defaultEdgeType: 'curve'
  }).graphProperties({
    minNodeSize: 0.5,
    maxNodeSize: 7,
    minEdgeSize: .25,
    maxEdgeSize: .5,
    sideMargin: 50
  }).mouseProperties({
    maxRatio: 32
  });
 
  // Parse a GEXF encoded file to fill the graph
  // (requires "sigma.parseGexf.js" to be included)
  sigInst.parseGexf(graphFile);
 
  // Draw the graph :
  sigInst.draw();
}
