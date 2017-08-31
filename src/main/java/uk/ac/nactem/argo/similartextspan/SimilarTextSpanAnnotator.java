/**
 * Similar Text Span Annotator - Marks up yet unannotated text spans which match the covered text of supplied annotations.
 * Copyright © 2017 The National Centre for Text Mining (NaCTeM), University of Manchester (jacob.carter@manchester.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.nactem.argo.similartextspan;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ResourceMetaData;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

/**
 * Marks up yet unannotated text spans which match the covered text of supplied annotations
 * 
 * @author National Centre for Text Mining (NaCTeM)
 */
@ResourceMetaData(name="Similar Text Span Annotator")
public class SimilarTextSpanAnnotator extends JCasAnnotator_ImplBase {
	public static final String PARAM_NAME_SOURCE_TYPE = "SourceType";
	@ConfigurationParameter(name = PARAM_NAME_SOURCE_TYPE,  mandatory = true)
	private String sourceTypeString;
	
	public static final String PARAM_NAME_TARGET_TYPE = "TargetType";
	@ConfigurationParameter(name = PARAM_NAME_TARGET_TYPE,  mandatory = true)
	private String targetTypeString;
	
	public static final String PARAM_NAME_RESPECT_WORD_BOUNDARIES = "RespectWordBoundaries";
	@ConfigurationParameter(name = PARAM_NAME_RESPECT_WORD_BOUNDARIES,  mandatory = true)
	private Boolean respectWordBoundaries;
	
	public static final String PARAM_NAME_CASE_SENSITIVE = "CaseSensitive";
	@ConfigurationParameter(name = PARAM_NAME_CASE_SENSITIVE,  mandatory = true)
	private Boolean caseSensitive;	
	
	private TypeSystem ts = null;

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	public void process(JCas jcas) throws AnalysisEngineProcessException {
		ts = jcas.getTypeSystem();
		Type sourceType = ts.getType(sourceTypeString);
		if (sourceType == null) {
			throw new AnalysisEngineProcessException(
					new Exception("Type " + sourceTypeString + " is not defined in the type system."));
		}

		Type targetType = ts.getType(targetTypeString);
		if (targetType == null) {
			throw new AnalysisEngineProcessException(
					new Exception("Type " + targetTypeString + " is not defined in the type system."));
		}

		Map<String, Annotation> givenAnnotations = new HashMap<String, Annotation>();
		Map<String, Annotation> newAnnotations = new HashMap<String, Annotation>();
		FSIterator<Annotation> sourceTypeIterator = jcas.getAnnotationIndex(sourceType).iterator();
		while (sourceTypeIterator.hasNext()) {
			Annotation sourceAnnotation = sourceTypeIterator.next();
			givenAnnotations.put(sourceAnnotation.getBegin() + "-" + sourceAnnotation.getEnd(), sourceAnnotation);
		}

		sourceTypeIterator = jcas.getAnnotationIndex(sourceType).iterator();
		while (sourceTypeIterator.hasNext()) {
			Annotation sourceAnnotation = sourceTypeIterator.next();
			String sourceTextSpan = Pattern.quote(sourceAnnotation.getCoveredText());
			String literal = (respectWordBoundaries ? "\\b" : "") + sourceTextSpan
					+ (respectWordBoundaries ? "\\b" : "");
			Pattern regex = null;
			if (caseSensitive) {
				regex = Pattern.compile(literal);
			} else {
				regex = Pattern.compile(literal, Pattern.CASE_INSENSITIVE);
			}

			FSIterator<Annotation> targetTypeIterator = jcas.getAnnotationIndex(targetType).iterator();
			while (targetTypeIterator.hasNext()) {
				Annotation targetAnnotation = targetTypeIterator.next();
				if (targetAnnotation.getBegin() != sourceAnnotation.getBegin()
						&& targetAnnotation.getEnd() != sourceAnnotation.getEnd()) {
					String targetTextSpan = targetAnnotation.getCoveredText();
					Matcher matcher = regex.matcher(targetTextSpan);
					while (matcher.find()) {
						try {
							Annotation annotation = (Annotation) Class.forName(sourceTypeString)
									.getConstructor(JCas.class).newInstance(jcas);
							annotation.setBegin(targetAnnotation.getBegin() + matcher.start());
							annotation.setEnd(targetAnnotation.getBegin() + matcher.end());
							if (givenAnnotations.get(annotation.getBegin() + "-" + annotation.getEnd()) == null) {
								newAnnotations.put(annotation.getBegin() + "-" + annotation.getEnd(), annotation);
							}
						} catch (Exception e) {
							e.printStackTrace();
							throw new AnalysisEngineProcessException(
									new Exception("Unable to create " + sourceTypeString + " annotation."));
						}
					}
				}
			}
		}

		Collection<Annotation> valueSet = newAnnotations.values();
		for (Annotation annotation : valueSet) {
			jcas.addFsToIndexes(annotation);
		}
	}
}
