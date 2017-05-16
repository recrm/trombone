/**
 * 
 */
package org.voyanttools.trombone.tool.corpus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.voyanttools.trombone.lucene.CorpusMapper;
import org.voyanttools.trombone.model.CorpusTerm;
import org.voyanttools.trombone.model.CorpusTermsCorrelation;
import org.voyanttools.trombone.model.Keywords;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.util.FlexibleParameters;
import org.voyanttools.trombone.util.FlexibleQueue;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import edu.stanford.nlp.util.ArrayUtils;

/**
 * @author sgs
 *
 */
@XStreamAlias("termCorrelations")
@XStreamConverter(CorpusTermCorrelations.CorpusTermCorrelationsConverter.class)
public class CorpusTermCorrelations extends AbstractTerms {
	
	private List<CorpusTermsCorrelation> correlations = new ArrayList<CorpusTermsCorrelation>();
	
	private float minInDocumentsCountRatio;

	/**
	 * @param storage
	 * @param parameters
	 */
	public CorpusTermCorrelations(Storage storage, FlexibleParameters parameters) {
		super(storage, parameters);
		minInDocumentsCountRatio = parameters.getParameterFloatValue("minInDocumentsCountRatio", 0);
	}

	/* (non-Javadoc)
	 * @see org.voyanttools.trombone.tool.corpus.AbstractTerms#runQueries(org.voyanttools.trombone.lucene.CorpusMapper, org.voyanttools.trombone.model.Keywords, java.lang.String[])
	 */
	@Override
	protected void runQueries(CorpusMapper corpusMapper, Keywords stopwords, String[] queries) throws IOException {
		if (corpusMapper.getCorpus().size()<2) {return;}
		CorpusTerms corpusTermsTool = getCorpusTerms();
		corpusTermsTool.runQueries(corpusMapper, stopwords, queries);
		List<CorpusTerm> outerCorpusTerms = corpusTermsTool.getCorpusTerms();
		corpusTermsTool = getCorpusTerms();
		corpusTermsTool.runAllTerms(corpusMapper, stopwords);
		List<CorpusTerm> innerCorpusTerms = corpusTermsTool.getCorpusTerms();
		populate(outerCorpusTerms, innerCorpusTerms, false, corpusMapper.getCorpus().size());
	}
	
	private void populate(List<CorpusTerm> outerList, List<CorpusTerm> innerList, boolean half, int docsCount) {
		PearsonsCorrelation correlationer = new PearsonsCorrelation();
		Comparator<CorpusTermsCorrelation> comparator = CorpusTermsCorrelation.getComparator(CorpusTermsCorrelation.Sort.getForgivingly(parameters));
		FlexibleQueue<CorpusTermsCorrelation> queue = new FlexibleQueue<CorpusTermsCorrelation>(comparator, start+limit);
		for (CorpusTerm outer : outerList) {
			if ((outer.getInDocumentsCount()*100)/docsCount<minInDocumentsCountRatio) {continue;}
			for (CorpusTerm inner : innerList) {
				if ((inner.getInDocumentsCount()*100)/docsCount<minInDocumentsCountRatio) {continue;}
				if (outer.getTerm().equals(inner.getTerm())==true) {continue;}
				if (!half || (half && outer.getTerm().compareTo(inner.getTerm())>0)) {
//					double correlation = pearsonCorrelation.correlation(ArrayUtils.toDouble(outer.getRelativeDistributions()), ArrayUtils.toDouble(inner.getRelativeDistributions()));
					double correlation = correlationer.correlation(ArrayUtils.toDouble(outer.getRelativeDistributions()), ArrayUtils.toDouble(inner.getRelativeDistributions()));
					total++;
					queue.offer(new CorpusTermsCorrelation(inner, outer, (float) correlation));
				}
			}
		}
		correlations.addAll(queue.getOrderedList());
	}
	private CorpusTerms getCorpusTerms() {
		FlexibleParameters params = new FlexibleParameters();
		params.addParameter("withDistributions", "relative");
		params.addParameter("minRawFreq", 2);
		return new CorpusTerms(storage, params);
	}

	/* (non-Javadoc)
	 * @see org.voyanttools.trombone.tool.corpus.AbstractTerms#runAllTerms(org.voyanttools.trombone.lucene.CorpusMapper, org.voyanttools.trombone.model.Keywords)
	 */
	@Override
	protected void runAllTerms(CorpusMapper corpusMapper, Keywords stopwords) throws IOException {
		if (corpusMapper.getCorpus().size()<2) {return;}
		CorpusTerms corpusTermsTool = getCorpusTerms();
		corpusTermsTool.runAllTerms(corpusMapper, stopwords);
		populate(corpusTermsTool.getCorpusTerms(), corpusTermsTool.getCorpusTerms(), true, corpusMapper.getCorpus().size());
	}
	public static class CorpusTermCorrelationsConverter implements Converter {

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.ConverterMatcher#canConvert(java.lang.Class)
		 */
		@Override
		public boolean canConvert(Class type) {
			return CorpusTermCorrelations.class.isAssignableFrom(type);
		}

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.Converter#marshal(java.lang.Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter, com.thoughtworks.xstream.converters.MarshallingContext)
		 */
		@Override
		public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
			CorpusTermCorrelations corpusTermCorrelations = (CorpusTermCorrelations) source;
			
	        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "total", Integer.class);
			writer.setValue(String.valueOf(corpusTermCorrelations.getTotal()));
			writer.endNode();
			
			FlexibleParameters parameters = corpusTermCorrelations.getParameters();
			boolean termsOnly = parameters.getParameterBooleanValue("termsOnly");
//			termsOnly = true;
			
			
	        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "correlations", Map.class);
			for (CorpusTermsCorrelation corpusTermCorrelation : corpusTermCorrelations.correlations) {
		        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "correlations", String.class); // not written in JSON
		        
		        int i = 0;
		        for (CorpusTerm corpusTerm : corpusTermCorrelation.getCorpusTerms()) {
			        ExtendedHierarchicalStreamWriterHelper.startNode(writer, i++==0 ? "source" : "target", String.class);
			        if (termsOnly) {
						writer.setValue(corpusTerm.getTerm());
			        } else {
				        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "term", String.class);
						writer.setValue(corpusTerm.getTerm());
						writer.endNode();
				        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "inDocumentsCount", Integer.class);
						writer.setValue(String.valueOf(corpusTerm.getInDocumentsCount()));
						writer.endNode();
				        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "rawFreq", Integer.class);
						writer.setValue(String.valueOf(corpusTerm.getRawFreq()));
						writer.endNode();
				        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "relativePeakedness", Float.class);
						writer.setValue(String.valueOf(corpusTerm.getPeakedness()));
						writer.endNode();
				        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "relativeSkewness", Float.class);
						writer.setValue(String.valueOf(corpusTerm.getSkewness()));
						writer.endNode();
				        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "distributions", List.class);
				        context.convertAnother(corpusTerm.getRelativeDistributions());
				        writer.endNode();
			        }
					writer.endNode();
		        }
		        
		        ExtendedHierarchicalStreamWriterHelper.startNode(writer, "correlation", Integer.class);
				writer.setValue(String.valueOf(corpusTermCorrelation.getCorrelation()));
				writer.endNode();
				
				writer.endNode();
			}
			writer.endNode();
		}

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.Converter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader, com.thoughtworks.xstream.converters.UnmarshallingContext)
		 */
		@Override
		public Object unmarshal(HierarchicalStreamReader arg0,
				UnmarshallingContext arg1) {
			// TODO Auto-generated method stub
			return null;
		}

	}

}
