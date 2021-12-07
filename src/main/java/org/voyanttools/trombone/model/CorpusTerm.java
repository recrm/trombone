package org.voyanttools.trombone.model;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.Comparator;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.voyanttools.trombone.tool.util.ToolSerializer;
import org.voyanttools.trombone.util.FlexibleParameters;

import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

@XStreamConverter(CorpusTerm.CorpusTermConverter.class)
public class CorpusTerm implements Serializable {

	public enum Sort {
		INDOCUMENTSCOUNTASC, INDOCUMENTSCOUNTDESC, RAWFREQASC, RAWFREQDESC, TERMASC, TERMDESC, RELATIVEPEAKEDNESSASC, RELATIVEPEAKEDNESSDESC, RELATIVESKEWNESSASC, RELATIVESKEWNESSDESC, COMPARISONRELATIVEFREQDIFFERENCEASC, COMPARISONRELATIVEFREQDIFFERENCEDESC;

		public static Sort getForgivingly(FlexibleParameters parameters) {
			String sort = parameters.getParameterValue("sort", "").toUpperCase();
			String sortPrefix = "RAWFREQ"; // default
			if (sort.startsWith("TERM")) {sortPrefix = "TERM";}
			else if (sort.startsWith("INDOCUMENTSCOUNT")) {sortPrefix = "INDOCUMENTSCOUNT";}
			else if (sort.startsWith("RELATIVEPEAK")) {sortPrefix = "RELATIVEPEAKEDNESS";}
			else if (sort.startsWith("RELATIVESKEW")) {sortPrefix = "RELATIVESKEWNESS";}
			else if (sort.startsWith("COMPARISONRELATIVEFREQDIFFERENCE")) {sortPrefix = "COMPARISONRELATIVEFREQDIFFERENCE";}
			String dir = parameters.getParameterValue("dir", "").toUpperCase();
			String dirSuffix = "DESC";
			if (dir.endsWith("ASC")) {dirSuffix="ASC";}
			return valueOf(sortPrefix+dirSuffix);
		}

		public boolean needDistributions() {			
			return this==RELATIVEPEAKEDNESSASC || this==RELATIVEPEAKEDNESSDESC || this==RELATIVESKEWNESSASC || this==RELATIVESKEWNESSDESC;
		}
	}

	private String term;
	private int rawFreq;
	private int totalTokens;
	private int[] rawFreqs;
	
	private int inDocumentsCount;
	private int totalDocuments;
	
	private float relativePeakedness = Float.NaN;
	
	private float relativeSkewness = Float.NaN;
	
	private float comparisonCorpusRelativeFrequencyDifference = Float.NaN;
	
//	private float[] relativeFreqs;
	
	@XStreamOmitField
	private transient String normalizedString = null;
	
	@XStreamOmitField
	private transient DescriptiveStatistics relativeStats = null;
	
	public CorpusTerm(String termString, int rawFreq, int totalTokens, int inDocumentsCount, int totalDocuments) {
		this(termString, rawFreq, totalTokens, inDocumentsCount, totalDocuments, null, null, 0);
	}
	
	public void setComparisonRelativeFrequency(float comparisonCorpusRelativeFrequency) {
		this.comparisonCorpusRelativeFrequencyDifference = this.getRelativeFrequency() - comparisonCorpusRelativeFrequency;
	}
	
	public float getComparisonCorpusRelativeFrequencyDifference() {
		return comparisonCorpusRelativeFrequencyDifference;
	}
	
	public CorpusTerm(String termString, int rawFreq, int totalTokens, int inDocumentsCount, int totalDocuments, int[] rawFreqs, float[] relativeFreqs, int bins) {
		this.term = termString;
		this.rawFreq = rawFreq;
		this.totalTokens = totalTokens;
		this.inDocumentsCount = inDocumentsCount;
		this.totalDocuments = totalDocuments;
		if (rawFreqs==null || rawFreqs.length==0 || bins==0 || rawFreqs.length==bins) {
			this.rawFreqs = rawFreqs;
		}
		else {
			this.rawFreqs = new int[bins];
			for(int position=0, len=rawFreqs.length; position<len; position++) {
				this.rawFreqs[(int) (position*bins/len)]+=rawFreqs[position];
			}
		}
		if (relativeFreqs==null || relativeFreqs.length==0 || bins==0) {
			// do nothing, keep stats as null
		}
		else if (bins==0 || bins==relativeFreqs.length) {
			this.relativeStats = new DescriptiveStatistics(relativeFreqs.length);
			for (float f : relativeFreqs) {relativeStats.addValue(f);}
		}
		else {
			SummaryStatistics[] stats = new SummaryStatistics[bins];
			for(int position=0, len=rawFreqs.length; position<len; position++) {
				int pos = (int) (position*bins/len);
				if (stats[pos]==null) {stats[pos]=new SummaryStatistics();}
				stats[pos].addValue(relativeFreqs[position]);
			}
			this.relativeStats = new DescriptiveStatistics(bins);
			for (SummaryStatistics stat : stats) {relativeStats.addValue(stat==null ? 0 : stat.getMean());}
		}
	}

	public CorpusTerm(CorpusTermMinimal corpusTermMinimal, int totalTokens) {
		this(corpusTermMinimal.getTerm(), corpusTermMinimal.getRawFreq(), totalTokens, corpusTermMinimal.getInDocumentsCount(), corpusTermMinimal.getDocumentsCount(), null, null, 0);
	}

	public int getRawFrequency() {
		return this.rawFreq;
	}
	@Deprecated
	public int getRawFreq() {
		return this.getRawFrequency();
	}
	
	public float getRelativeFrequency() {
		return (float) rawFreq / (float) totalTokens;
	}
	@Deprecated
	public float getRelativeFreq() {
		return this.getRelativeFrequency();
	}
	
	private String getNormalizedTerm() {
		if (normalizedString==null) {normalizedString = Normalizer.normalize(term, Normalizer.Form.NFD);}
		return normalizedString;
	}

	public String getTerm() {
		return term;
	}
	
	public float getPeakedness() {
		if (Float.isNaN(relativePeakedness) && relativeStats!=null) {
			relativePeakedness = (float) relativeStats.getKurtosis();
		}
		return relativePeakedness;
	}
	
	public float getSkewness() {
		if (Float.isNaN(relativeSkewness) && relativeStats!=null) {
			relativeSkewness = (float) relativeStats.getSkewness();
		}
		return relativeSkewness;
	}
	
	public int[] getRawDistributions() {
		return rawFreqs;
	}

	public float[] getRelativeDistributions() {
		if (relativeStats==null || relativeStats.getN()==0) {
			return new float[0];
		}
		float[] distributions = new float[(int) relativeStats.getN()];
		for(int position=0, len=distributions.length; position<len; position++) {
			distributions[position]=(float) relativeStats.getElement(position); // TODO: this needs to be averaged?
		}
		return distributions;
	}
	
	public int getInDocumentsCount() {
		return inDocumentsCount;
	}
	
	public static Comparator<CorpusTerm> getComparator(Sort sort) {
		switch (sort) {
		case RAWFREQASC:
			return RawFrequencyAscendingComparator;
		case TERMASC:
			return TermAscendingComparator;
		case RELATIVEPEAKEDNESSASC:
			return RelativePeakednessAscendingComparator;
		case RELATIVEPEAKEDNESSDESC:
			return RelativePeakednessDescendingComparator;
		case RELATIVESKEWNESSASC:
			return RelativeSkewnessAscendingComparator;
		case RELATIVESKEWNESSDESC:
			return RelativeSkewnessDescendingComparator;
		case TERMDESC:
			return TermDescendingComparator;
		case INDOCUMENTSCOUNTASC:
			return InDocumentsCountAscendingComparator;
		case INDOCUMENTSCOUNTDESC:
			return InDocumentsCountDescendingComparator;
		case COMPARISONRELATIVEFREQDIFFERENCEASC:
			return ComparisonCorpusRelativeFrequencyAscendingComparator;
		case COMPARISONRELATIVEFREQDIFFERENCEDESC:
			return ComparisonCorpusRelativeFrequencyDescendingComparator;
		default: // rawFrequencyDesc
			return RawFrequencyDescendingComparator;
		}
	}
	
	private static Comparator<CorpusTerm> TermAscendingComparator = new Comparator<CorpusTerm>() {
		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			int i = term2.getNormalizedTerm().compareTo(term1.getNormalizedTerm());
			if (i==0) {
				return term1.rawFreq - term2.rawFreq;
			}
			return i;
		}
	};

	private static Comparator<CorpusTerm> TermDescendingComparator = new Comparator<CorpusTerm>() {
		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			int i = term1.getNormalizedTerm().compareTo(term2.getNormalizedTerm());
			if (i==0) {
				return term1.rawFreq - term2.rawFreq;
			}
			return i;
		}
	};

	private static Comparator<CorpusTerm> RawFrequencyDescendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			if (term1.rawFreq==term2.rawFreq) {
				return term1.getNormalizedTerm().compareTo(term2.getNormalizedTerm());
			}
			else {
				return term2.rawFreq - term1.rawFreq;
			}
		}
		
	};

	private static Comparator<CorpusTerm> RawFrequencyAscendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			if (term1.rawFreq==term2.rawFreq) {
				return term2.getNormalizedTerm().compareTo(term1.getNormalizedTerm());
			}
			else {
				return term1.rawFreq - term2.rawFreq;
			}
		}
		
	};
	
	private static Comparator<CorpusTerm> InDocumentsCountAscendingComparator = new Comparator<CorpusTerm>() {
		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			if (term1.inDocumentsCount==term2.inDocumentsCount) {
				return term1.getNormalizedTerm().compareTo(term2.getNormalizedTerm());
			}
			else {
				return term1.inDocumentsCount - term2.inDocumentsCount;
			}
		}
		
	};
	private static Comparator<CorpusTerm> InDocumentsCountDescendingComparator = new Comparator<CorpusTerm>() {
		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			if (term1.inDocumentsCount==term2.inDocumentsCount) {
				return term1.getNormalizedTerm().compareTo(term2.getNormalizedTerm());
			}
			else {
				return term2.inDocumentsCount - term1.inDocumentsCount;
			}
		}
		
	};
	

	private static Comparator<CorpusTerm> RelativePeakednessAscendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			float f1 = term1.getPeakedness();
			float f2 = term2.getPeakedness();
			if (f1==f2) {
				return RawFrequencyDescendingComparator.compare(term1, term2);
			}
			else {
				return Float.compare(f1, f2);
			}
		}	
	};
	
	private static Comparator<CorpusTerm> RelativePeakednessDescendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			float f1 = term1.getPeakedness();
			float f2 = term2.getPeakedness();
			if (f1==f2) {
				return RawFrequencyDescendingComparator.compare(term1, term2);
			}
			else {
				return Float.compare(f2, f1);
			}
		}	
	};
	
	private static Comparator<CorpusTerm> RelativeSkewnessDescendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			float f1 = term1.getSkewness();
			float f2 = term2.getSkewness();
			if (f1==f2) {
				return RawFrequencyDescendingComparator.compare(term1, term2);
			}
			else {
				return Float.compare(f2, f1);
			}
		}	
	};

	private static Comparator<CorpusTerm> RelativeSkewnessAscendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			float f1 = term1.getSkewness();
			float f2 = term2.getSkewness();
			if (f1==f2) {
				return RawFrequencyDescendingComparator.compare(term1, term2);
			}
			else {
				return Float.compare(f1, f2);
			}
		}	
	};
	
	private static Comparator<CorpusTerm> ComparisonCorpusRelativeFrequencyDescendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			float f1 = term1.getComparisonCorpusRelativeFrequencyDifference();
			float f2 = term2.getComparisonCorpusRelativeFrequencyDifference();
			if (f1==f2) {
				return RawFrequencyDescendingComparator.compare(term1, term2);
			}
			else {
				return Float.compare(f2, f1);
			}
		}	
	};

	private static Comparator<CorpusTerm> ComparisonCorpusRelativeFrequencyAscendingComparator = new Comparator<CorpusTerm>() {

		@Override
		public int compare(CorpusTerm term1, CorpusTerm term2) {
			float f1 = term1.getComparisonCorpusRelativeFrequencyDifference();
			float f2 = term2.getComparisonCorpusRelativeFrequencyDifference();
			if (f1==f2) {
				return RawFrequencyDescendingComparator.compare(term1, term2);
			}
			else {
				return Float.compare(f1, f2);
			}
		}	
	};

	
	@Override
	public String toString() {
		return "{"+term+": "+rawFreq+" ("+getRelativeFrequency()+")";
	}
	
	public static class CorpusTermConverter implements Converter {

		@Override
		public boolean canConvert(Class type) {
			return CorpusTerm.class.isAssignableFrom(type);
		}

		@Override
		public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
			CorpusTerm corpusTerm = (CorpusTerm) source;
			
			boolean withRawDistributions = Boolean.TRUE.equals(context.get("withRawDistributions"));
			boolean withRelativeDistributions = Boolean.TRUE.equals(context.get("withRelativeDistributions"));
			boolean inDocumentsCountOnly = Boolean.TRUE.equals(context.get("inDocumentsCountOnly"));
			
			writer.startNode("term");
			
			writer.startNode("term");
			writer.setValue(corpusTerm.getTerm());
			writer.endNode();
			
			ToolSerializer.setNumericNode(writer, "inDocumentsCount", corpusTerm.getInDocumentsCount());
			
			if (!inDocumentsCountOnly) {
				
				ToolSerializer.setNumericNode(writer, "rawFreq", corpusTerm.getRawFrequency());
				
				ToolSerializer.setNumericNode(writer, "relativeFreq", corpusTerm.getRelativeFrequency());

				ToolSerializer.setNumericNode(writer, "comparisonRelativeFreqDifference", corpusTerm.getComparisonCorpusRelativeFrequencyDifference());
				
				
				if (withRawDistributions || withRelativeDistributions) {
					
					ToolSerializer.setNumericNode(writer, "relativePeakedness", corpusTerm.getPeakedness());
					
					ToolSerializer.setNumericNode(writer, "relativeSkewness", corpusTerm.getSkewness());
					
					if (withRawDistributions) {
						ToolSerializer.setNumericList(writer, "distributions", corpusTerm.getRawDistributions());
					} else {
						ToolSerializer.setNumericList(writer, "distributions", corpusTerm.getRelativeDistributions());
					}
				}

			}
			
			writer.endNode();
		}

		@Override
		public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

}
