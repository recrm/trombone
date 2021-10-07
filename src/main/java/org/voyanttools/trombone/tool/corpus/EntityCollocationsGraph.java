/**
 * 
 */
package org.voyanttools.trombone.tool.corpus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.voyanttools.trombone.lucene.CorpusMapper;
import org.voyanttools.trombone.model.CorpusEntity;
import org.voyanttools.trombone.model.DocumentEntity;
import org.voyanttools.trombone.model.EntityType;
import org.voyanttools.trombone.model.Keywords;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.util.FlexibleParameters;
import org.voyanttools.trombone.util.FlexibleQueue;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * @author sgs
 *
 */
@XStreamAlias("entityCollocationsGraph")
public class EntityCollocationsGraph extends AbstractTerms {
	
	private List<Edge> edges = new ArrayList<Edge>();
	private List<CorpusEntity> nodes = new ArrayList<CorpusEntity>();

	/**
	 * @param storage
	 * @param parameters
	 */
	public EntityCollocationsGraph(Storage storage, FlexibleParameters parameters) {
		super(storage, parameters);
	}
	
	public float getVersion() {
		return super.getVersion()+1;
	}

	@Override
	protected void runQueries(CorpusMapper corpusMapper, Keywords stopwords, String[] queries) throws IOException {
		doRun(corpusMapper, stopwords);
	}

	@Override
	protected void runAllTerms(CorpusMapper corpusMapper, Keywords stopwords) throws IOException {
		doRun(corpusMapper, stopwords);
	}

	private void doRun(CorpusMapper corpusMapper, Keywords stopwords) throws IOException {
		DocumentEntities documentEntitiesTool = new DocumentEntities(storage, parameters);
		documentEntitiesTool.run(corpusMapper);
		List<DocumentEntity> documentEntities = documentEntitiesTool.getDocumentEntities();
		
		
		// organize by document and track counts
		Map<Integer, List<DocumentEntity>> entitesByDocumentMap = new HashMap<Integer, List<DocumentEntity>>();
		for (DocumentEntity entity : documentEntities) {
			if (stopwords.isKeyword(entity.getTerm().toLowerCase()) == false) {
				int docIndex = entity.getDocIndex();
				if (!entitesByDocumentMap.containsKey(docIndex)) {
					entitesByDocumentMap.put(docIndex, new ArrayList<DocumentEntity>());
				}
				entitesByDocumentMap.get(docIndex).add(entity);
			}
		}
		
		// create intra-document links
		Map<String, List<DocumentEntity[]>> termEntitiesMap = new HashMap<String, List<DocumentEntity[]>>();
		for (List<DocumentEntity> docEntities : entitesByDocumentMap.values()) {
			Collections.sort(docEntities);
			for (DocumentEntity outer : docEntities) {
				for (DocumentEntity inner : docEntities) {
					if (inner.compareTo(outer)<0) {
						String key = outer.getTerm()+" -- "+inner.getTerm();
						if (!termEntitiesMap.containsKey(key)) {
							termEntitiesMap.put(key, new ArrayList<DocumentEntity[]>());
						}
						termEntitiesMap.get(key).add(new DocumentEntity[]{outer,inner});
					}
					else {
						break;
					}
				}
			}
		}
		
		// create inter-document links
		CorpusEntities corpusEntitiesTool = new CorpusEntities(storage, parameters);
		List<CorpusEntity> candidateNodes = corpusEntitiesTool.getCorpusEntities(documentEntities);
		Map<String, CorpusEntity> corpusEntitiesMap = new HashMap<String, CorpusEntity>();
		for (CorpusEntity corpusEntity : candidateNodes) {
			if (stopwords.isKeyword(corpusEntity.getTerm().toLowerCase()) == false) {
				corpusEntitiesMap.put(getEntityKey(corpusEntity.getTerm(), corpusEntity.getType()), corpusEntity);
			}
		}
		
		FlexibleQueue<Edge> edgesQueue = new FlexibleQueue<Edge>(new Comparator<Edge>() {
			@Override
			public int compare(Edge o1, Edge o2) {
				return o2.compareTo(o1);
			}
		}, start+limit);
		
		int minEdgeCount = parameters.getParameterIntValue("minEdgeCount", 2);
		for (List<DocumentEntity[]> termEntitesList : termEntitiesMap.values()) {
			if (termEntitesList.size()<minEdgeCount) {continue;}
			DocumentEntity[] docEntities = termEntitesList.get(0);
			String key1 = getEntityKey(docEntities[0].getTerm(), docEntities[0].getType());
			String key2 = getEntityKey(docEntities[1].getTerm(), docEntities[1].getType());
			if (corpusEntitiesMap.containsKey(key1) && corpusEntitiesMap.containsKey(key2)) {
				edgesQueue.offer(new Edge(new CorpusEntity[]{corpusEntitiesMap.get(key1), corpusEntitiesMap.get(key2)}, termEntitesList.size()));
			}
		}
		
		edges = edgesQueue.getOrderedList(start);
		
		Map<CorpusEntity, Integer> corpusEntitiesToIndexMap = new LinkedHashMap<CorpusEntity, Integer>(); 
		int counter = 0;
		for (Edge edge : edges) {
			for (CorpusEntity corpusEntity : edge.corpusEntities) {
				if (corpusEntitiesToIndexMap.containsKey(corpusEntity)==false) {
					corpusEntitiesToIndexMap.put(corpusEntity, counter++);
				}
			}
			edge.nodes = new int[]{corpusEntitiesToIndexMap.get(edge.corpusEntities[0]), corpusEntitiesToIndexMap.get(edge.corpusEntities[1])};
		}
		
		nodes.addAll(corpusEntitiesToIndexMap.keySet());
	}
	
	private String getEntityKey(String term, EntityType type) {
		return term+"--"+type.name();
	}
	
	private static class Edge implements Comparable<Edge> {

		@XStreamOmitField
		private transient CorpusEntity[] corpusEntities;
		
		private int[] nodes = new int[2];
		private int count;
		private Edge(CorpusEntity[] corpusEntities, int count) {
			this.corpusEntities = corpusEntities;
			this.count = count;
		}
		
		@Override
		public int compareTo(Edge o) {
			int i = Integer.compare(count, o.count);
			if (i==0) {
				return Integer.compare(corpusEntities[0].getRawFreq()+corpusEntities[1].getRawFreq(),o.corpusEntities[0].getRawFreq()+o.corpusEntities[1].getRawFreq());
			}
			else {
				return i;
			}
		}
	}

}
