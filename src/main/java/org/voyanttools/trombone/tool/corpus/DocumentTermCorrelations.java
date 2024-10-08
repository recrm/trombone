package org.voyanttools.trombone.tool.corpus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.voyanttools.trombone.lucene.CorpusMapper;
import org.voyanttools.trombone.model.DocumentTerm;
import org.voyanttools.trombone.model.DocumentTermsCorrelation;
import org.voyanttools.trombone.model.Keywords;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.tool.util.Message;
import org.voyanttools.trombone.tool.util.ToolSerializer;
import org.voyanttools.trombone.util.FlexibleParameters;
import org.voyanttools.trombone.util.FlexibleQueue;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

@XStreamAlias("termCorrelations")
@XStreamConverter(DocumentTermCorrelations.DocumentTermCorrelationsConverter.class)
public class DocumentTermCorrelations extends AbstractTerms {

	@XStreamOmitField
	private int distributionBins;
	
	private List<DocumentTermsCorrelation> correlations;
	
	@XStreamOmitField
	private long startTime;
	
	@XStreamOmitField
	private static long maxRunTimeMilliseconds = -1;
	
	@XStreamOmitField
	private final static String LIMIT_ENTRY = "termCorrelationsMaxTime";
	
	@XStreamOmitField
	private final static long DEFAULT_MAX_RUN_TIME = 20000;
	
	public DocumentTermCorrelations(Storage storage, FlexibleParameters parameters) {
		super(storage, parameters);
		distributionBins = parameters.getParameterIntValue("bins", 10);
		correlations = new ArrayList<DocumentTermsCorrelation>();
		if (limit==Integer.MAX_VALUE) { // don't allow no limit
			message(Message.Type.WARN, "mandatoryLimit", "This tool can't be called with no limit to the number of correlations, so the limit has been set to 10,000");
			limit = 10000;
		}
		if (DocumentTermCorrelations.maxRunTimeMilliseconds == -1) {
			try {
				String limit = this.getToolLimits(LIMIT_ENTRY);
				DocumentTermCorrelations.maxRunTimeMilliseconds = Long.parseLong(limit);
			} catch (Exception e) {
				DocumentTermCorrelations.maxRunTimeMilliseconds = DEFAULT_MAX_RUN_TIME;
			}
		}
	}
	
	public float getVersion() {
		return super.getVersion()+2;
	}
	
	@Override
	public void run(CorpusMapper corpusMapper) throws IOException {
		this.startTime = System.currentTimeMillis();
		super.run(corpusMapper);
	}

	@Override
	protected void runQueries(CorpusMapper corpusMapper, Keywords stopwords, String[] queries) throws IOException {
		List<String> ids = this.getCorpusStoredDocumentIdsFromParameters(corpusMapper.getCorpus());
		DocumentTerms documentTermsTool = getDocumentTermsTool(null);
		documentTermsTool.runQueries(corpusMapper, stopwords, queries);
		List<DocumentTerm> outerList = documentTermsTool.getDocumentTerms();
		Comparator<DocumentTermsCorrelation> comparator = DocumentTermsCorrelation.getComparator(DocumentTermsCorrelation.Sort.getForgivingly(parameters));
		FlexibleQueue<DocumentTermsCorrelation> queue = new FlexibleQueue<DocumentTermsCorrelation>(comparator, start+limit);
		for (String id : ids) {
			documentTermsTool = getDocumentTermsTool(id);
			documentTermsTool.runAllTerms(corpusMapper, stopwords);
			List<DocumentTermsCorrelation> dtc = getDocumentTermsCorrelationList(outerList, documentTermsTool.getDocumentTerms(), true);
			for (DocumentTermsCorrelation d : dtc) {
				queue.offer(d);
			}
		}
		correlations = queue.getOrderedList(start);
	}

	@Override
	protected void runAllTerms(CorpusMapper corpusMapper, Keywords stopwords) throws IOException {
		List<String> ids = this.getCorpusStoredDocumentIdsFromParameters(corpusMapper.getCorpus());
		Comparator<DocumentTermsCorrelation> comparator = DocumentTermsCorrelation.getComparator(DocumentTermsCorrelation.Sort.getForgivingly(parameters));
		FlexibleQueue<DocumentTermsCorrelation> queue = new FlexibleQueue<DocumentTermsCorrelation>(comparator, start+limit);
		for (String id : ids) {
			DocumentTerms documentTermsTool = getDocumentTermsTool(id);
			documentTermsTool.runAllTerms(corpusMapper, stopwords);
			List<DocumentTermsCorrelation> dtc = getDocumentTermsCorrelationList(documentTermsTool.getDocumentTerms(), documentTermsTool.getDocumentTerms(), true);
			for (DocumentTermsCorrelation d : dtc) {
				queue.offer(d);
			}
		}
		correlations = queue.getOrderedList(start);
	}
	
	
	private DocumentTerms getDocumentTermsTool(String id) {
		FlexibleParameters params = new FlexibleParameters();
		params.setParameter("withDistributions", "relative");
		params.setParameter("minRawFreq", 2);
//		params.setParameter("perDocLimit", limit); // TODO consider setting perDocLimit instead of using max time limit
		if (id!=null) {params.setParameter("docId", id);}
		return new DocumentTerms(storage, params);
	}
	
	private List<DocumentTermsCorrelation> getDocumentTermsCorrelationList(List<DocumentTerm> outerList, List<DocumentTerm> innerList, boolean half) {
//		SpearmansCorrelation spearmansCorrelation = new SpearmansCorrelation();
		Comparator<DocumentTermsCorrelation> comparator = DocumentTermsCorrelation.getComparator(DocumentTermsCorrelation.Sort.getForgivingly(parameters));
		FlexibleQueue<DocumentTermsCorrelation> queue = new FlexibleQueue<DocumentTermsCorrelation>(comparator, start+limit);
		SimpleRegression regression = new SimpleRegression();
		boolean maxTimeHit = false;
		for (DocumentTerm outer : outerList) {
			if (maxTimeHit) break;
			for (DocumentTerm inner : innerList) {
				if (maxTimeHit) break;
				if (outer.getDocId().equals(inner.getDocId())==false) {continue;} // different docs, maybe from querying
				if (outer.equals(inner)) {continue;} // same word
				if (!half || (half && outer.getTerm().compareTo(inner.getTerm())>0)) {
					regression.clear();
					float[] outerCounts = outer.getRelativeDistributions(distributionBins);
					float[] innerCounts = inner.getRelativeDistributions(distributionBins);
					for (int i=0, len=outerCounts.length; i<len; i++) {
						regression.addData(outerCounts[i], innerCounts[i]);
					}
					queue.offer(new DocumentTermsCorrelation(inner, outer, (float) regression.getR(), (float) regression.getSignificance()));
					if (total % 10 == 0) {
						long currTime = System.currentTimeMillis();
						long diffTime = currTime - startTime;
						if (diffTime >= maxRunTimeMilliseconds) {
							message(Message.Type.WARN, "maxTime", "This tool has exceeded the maximum run time.");
							maxTimeHit = true;
						}
					}
					total++;
				}
			}
		}
		return queue.getOrderedList();
	}

	public static class DocumentTermCorrelationsConverter implements Converter {

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.ConverterMatcher#canConvert(java.lang.Class)
		 */
		@Override
		public boolean canConvert(Class type) {
			return DocumentTermCorrelations.class.isAssignableFrom(type);
		}

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.Converter#marshal(java.lang.Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter, com.thoughtworks.xstream.converters.MarshallingContext)
		 */
		@Override
		public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
			DocumentTermCorrelations documentTermCorrelations = (DocumentTermCorrelations) source;
			
			documentTermCorrelations.writeMessages(writer, context);
			
			ToolSerializer.startNode(writer, "total", Integer.class);
			writer.setValue(String.valueOf(documentTermCorrelations.getTotal()));
			ToolSerializer.endNode(writer);
			
			FlexibleParameters parameters = documentTermCorrelations.getParameters();
			boolean termsOnly = parameters.getParameterBooleanValue("termsOnly");
			boolean withDistributions = parameters.getParameterBooleanValue("withDistributions");
			
			context.put("termsOnly", termsOnly);
			context.put("withDistributions", withDistributions);
			context.put("distributionBins", documentTermCorrelations.distributionBins);
			
			
			ToolSerializer.startNode(writer, "correlations", Map.class);
			for (DocumentTermsCorrelation documentTermCorrelation : documentTermCorrelations.correlations) {
				context.convertAnother(documentTermCorrelation);
			}
			ToolSerializer.endNode(writer);
		}

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.Converter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader, com.thoughtworks.xstream.converters.UnmarshallingContext)
		 */
		@Override
		public Object unmarshal(HierarchicalStreamReader arg0, UnmarshallingContext arg1) {
			// TODO Auto-generated method stub
			return null;
		}

	}

	public Object getDocumentTerms() {
		// TODO Auto-generated method stub
		return null;
	}
}
