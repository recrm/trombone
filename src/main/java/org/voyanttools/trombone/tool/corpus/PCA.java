package org.voyanttools.trombone.tool.corpus;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.voyanttools.trombone.lucene.CorpusMapper;
import org.voyanttools.trombone.model.RawCATerm;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.tool.analysis.AnalysisUtils;
import org.voyanttools.trombone.tool.analysis.PrincipalComponentsAnalysis;
import org.voyanttools.trombone.tool.analysis.PrincipalComponentsAnalysis.PrincipleComponent;
import org.voyanttools.trombone.util.FlexibleParameters;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

@XStreamAlias("pcaAnalysis")
@XStreamConverter(PCA.PCAConverter.class)
public class PCA extends CorpusAnalysisTool {
	
	private PrincipalComponentsAnalysis pca;
	
	public PCA(Storage storage, FlexibleParameters parameters) {
		super(storage, parameters);
	}
	
	private double[][] doPCA(double[][] freqMatrix) {
	    pca = new PrincipalComponentsAnalysis(freqMatrix);
	    pca.runAnalysis();
	    return pca.getResult(dimensions);
	}

	@Override
	protected double[][] runAnalysis(CorpusMapper corpusMapper) throws IOException {
		double[][] freqMatrix = buildFrequencyMatrix(corpusMapper, MatrixType.TERM, 2);
		double[][] result = this.doPCA(freqMatrix);
		
		int i;
		for (i = 0; i < analysisTerms.size(); i++) {
			RawCATerm term = analysisTerms.get(i);
			term.setVector(result[i]);
			if (term.getTerm().equals(target)) targetVector = result[i];
		}
		
		return result;
	}
	
	public static class PCAConverter implements Converter {

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.ConverterMatcher#canConvert(java.lang.Class)
		 */
		@Override
		public boolean canConvert(Class type) {
			return PCA.class.isAssignableFrom(type);
		}

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.Converter#marshal(java.lang.Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter, com.thoughtworks.xstream.converters.MarshallingContext)
		 */
		@Override
		public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
			
			PCA pca = (PCA) source;
	        
			final List<RawCATerm> pcaTerms = pca.analysisTerms;
			
			final SortedSet<PrincipleComponent> principalComponents = pca.pca.getPrincipleComponents();
			
			ExtendedHierarchicalStreamWriterHelper.startNode(writer, "totalTerms", Integer.class);
			writer.setValue(String.valueOf(pcaTerms.size()));
			writer.endNode();
			
			ExtendedHierarchicalStreamWriterHelper.startNode(writer, "principalComponents", Map.Entry.class);
			for (PrincipleComponent pc : principalComponents) {
				writer.startNode("principalComponent");
				
				ExtendedHierarchicalStreamWriterHelper.startNode(writer, "eigenValue", Double.class);
				writer.setValue(String.valueOf(pc.eigenValue));
				writer.endNode();
				
				float[] vectorFloat = new float[pc.eigenVector.length];
				for (int i = 0, size = pc.eigenVector.length; i < size; i++)  {
					vectorFloat[i] = (float) pc.eigenVector[i];
				}
				ExtendedHierarchicalStreamWriterHelper.startNode(writer, "eigenVectors", vectorFloat.getClass());
				context.convertAnother(vectorFloat);
				writer.endNode();
				
				writer.endNode();
			}
			writer.endNode();
			
			AnalysisUtils.outputTerms(pcaTerms, false, writer, context);
	        

		}

		/* (non-Javadoc)
		 * @see com.thoughtworks.xstream.converters.Converter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader, com.thoughtworks.xstream.converters.UnmarshallingContext)
		 */
		@Override
		public Object unmarshal(HierarchicalStreamReader reader,
				UnmarshallingContext context) {
			return null;
		}
	}

}