package it.polito.tdp.extflightdelays.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgrapht.Graphs;
import org.jgrapht.event.ConnectedComponentTraversalEvent;
import org.jgrapht.event.EdgeTraversalEvent;
import org.jgrapht.event.TraversalListener;
import org.jgrapht.event.VertexTraversalEvent;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import it.polito.tdp.extflightdelays.db.ExtFlightDelaysDAO;

public class Model {

	private SimpleWeightedGraph<Airport, DefaultWeightedEdge> grafo;
	private Map<Integer, Airport> idMap;
	private ExtFlightDelaysDAO dao;
	
	private Map<Airport, Airport> visita = new HashMap<>();
	
	public Model() {
		this.idMap = new HashMap<>();
		this.dao = new ExtFlightDelaysDAO();
		this.dao.loadAllAirports(idMap);
	}
	
	public void creaGrafo(int x) {
		this.grafo = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
		
		//Aggiungiamo i vertici (solo quelli che rispettano il vincolo)
		for(Airport a : idMap.values()) {
			if(dao.getAirlinesNumber(a) > x) {
				this.grafo.addVertex(a);
			}
			//Mi faccio dare tutte le rotte dal DAO, sia da A a B che da B ad A.. poi le scorro e faccio il controllo
			//dei doppioni qua sotto... ovvero esiste gia' un arco tra A e B ? no quindi aggiungo.. esiste gia' un 
			//arco da B ad A ? Lo chiedo al grafo e dira' di si'.. perche' il grafo non e' orientato... allora non
			//vado ad inserire un nuovo arco, ma prendo il peso di questo arco e lo modifico con il peso della
			//rotta che sto considerando
			for(Rotta r : dao.getRotte(idMap)) {
				
				//Controlliamo pero' che prima di aggiungere un arco, i due vertici ci siano nel grafo
				if(this.grafo.containsVertex(r.getA1()) && this.grafo.containsVertex(r.getA2())) {
					DefaultWeightedEdge e = this.grafo.getEdge(r.getA1(), r.getA2());
					
					if(e == null) { //L'arco ancora non esisteva
						Graphs.addEdgeWithVertices(this.grafo, r.getA1(), r.getA2(), r.getPeso());
					}
					else {
						double pesoVecchio = this.grafo.getEdgeWeight(e);
						double pesoNuovo = pesoVecchio + r.getPeso();
						this.grafo.setEdgeWeight(e, pesoNuovo);
					}
				}
			}
		}
	}
	
	public int vertexNumber() {
		return this.grafo.vertexSet().size();
	}
	
	public int edgeNumber() {
		return this.grafo.edgeSet().size();
	}
	
	public Collection<Airport> getAirport() {
		return this.grafo.vertexSet();
	}
	
	/**
	 * Metodo che ritorna la lista di aeroporti che modella il percorso tra due aereoporti.. se e' null non saranno
	 * connessi, altrimenti vuol dire che lo sono e la lista e' il percorso
	 * @param a1 Partenza
	 * @param a2 Destinazione
	 * @return
	 */
	public List<Airport> trovaPercorso(Airport a1, Airport a2) {
		List<Airport> percorso = new ArrayList<>();
		
		//Dobbiamo visitare il grafo e mano a mano dobbiamo tenere traccia dell'albero di visita, non importa se in
		//Ampiezza o in Profondita. Dobbiamo usare un iteratore ed un TraversalListener, cosi' che possiamo definire
		//un metodo che viene automaticamente richiamato ogni volta che la nostra visita attraversa un arco del grafo
		
		//Per tenere traccia di solito usiamo una MAPPA per salvare l'albero di visite, cosi' da modellare le relazioni
		//padre e figlio. Nella mappa ho coppie di aereoporti che tengono traccia di connessioni, cosi' poi potro'
		//estrarre il percorso
		
		//Vertice sorgente suppongo a1
		BreadthFirstIterator<Airport, DefaultWeightedEdge> it = new BreadthFirstIterator<>(this.grafo, a1);
		
		//Aggiungo la "radice" del mio albero di visita
		visita.put(a1, null); //Cioe' partenza non e' raggiungibile da nessun nodo
		
		it.addTraversalListener(new TraversalListener<Airport, DefaultWeightedEdge>() {

			@Override
			public void connectedComponentFinished(ConnectedComponentTraversalEvent e) {}

			@Override
			public void connectedComponentStarted(ConnectedComponentTraversalEvent e) {}
			
			@Override
			/**
			 * Prendiamo il nodo sorgente e nodo destinazione dell'arco che stiamo attraversando in questo momento.
			 * Poi salviamo l'albero di visita.. quindi ci chiediamo se la nostra visita non contenga gia' la
			 * destinazione, ma contenga gia' il nodo sorgente -> allora la nostra destinazione si raggiunge da sorgente
			 * 
			 * Siccome pero' il grafo non e' orientato devo fare anche il caso opposto con l'else..
			 */
			public void edgeTraversed(EdgeTraversalEvent<DefaultWeightedEdge> e) {
				Airport sorgente = grafo.getEdgeSource(e.getEdge());
				Airport destinazione = grafo.getEdgeTarget(e.getEdge());
				
				if(!visita.containsKey(destinazione) && visita.containsKey(sorgente)) {
					visita.put(destinazione, sorgente);
				}
				else if(!visita.containsKey(sorgente) && visita.containsKey(destinazione)) {
					visita.put(sorgente, destinazione);
				}
			}

			@Override
			public void vertexTraversed(VertexTraversalEvent<Airport> e) {}

			@Override
			public void vertexFinished(VertexTraversalEvent<Airport> e) {}
			
		});
		
		while(it.hasNext())
			it.next();
		
		//Ora che la visita e' terminata abbiamo il nostro albero di visita, controlliamo se gli aeroporti sono
		//collegati oppure no..
		if(!visita.containsKey(a1) || !visita.containsKey(a2))
			return null; //Aeroporti non collegati
		
		//Altrimenti dobbiamo estrapolare il percorso.
		Airport step = a2; //(destinazione)
		
		//Finche' step non e' uguale alla partenza... cioe' parto dalla destinazione e risalgo nell'albero fin quando
		//non trovo la partenza
		while(!step.equals(a1)) {
			percorso.add(step);
			step = visita.get(step); //risalgo nella mappa.. prendo il nodo con cui potevo raggiungere il mio nodo step
		}
		percorso.add(a1); //Per aggiungere la partenza
		return percorso;
	}
}
