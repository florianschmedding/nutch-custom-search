package ir.co.bayan.simorq.zal.extractor.core;

import ir.co.bayan.simorq.zal.extractor.evaluation.CssEvaluator;
import ir.co.bayan.simorq.zal.extractor.evaluation.EvaluationContext;
import ir.co.bayan.simorq.zal.extractor.evaluation.Evaluator;
import ir.co.bayan.simorq.zal.extractor.evaluation.EvaluatorFactory;
import ir.co.bayan.simorq.zal.extractor.evaluation.XPathEvaluator;
import ir.co.bayan.simorq.zal.extractor.model.Document;
import ir.co.bayan.simorq.zal.extractor.model.ExtractorConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

/**
 * Extracts parts of an HTML or XML file based on the defined extract rules in the provided config file. Note that
 * although the configuration file has the types section, this class does not perform any type specific actions such as
 * type conversions. This class is thread-safe.
 * 
 * @see XPathEvaluator
 * @see CssEvaluator
 * 
 * @author Taha Ghasemi <taha.ghasemi@gmail.com>
 * 
 */
public class ExtractEngine {

	private final ExtractorConfig extractorConfig;
	private final Map<String, Document> docById;

	public ExtractEngine(ExtractorConfig extractorConfig) throws Exception {
		Validate.notNull(extractorConfig);

		this.extractorConfig = extractorConfig;
		docById = new HashMap<>(extractorConfig.getDocuments().size() * 2 + 1);
		for (Document doc : extractorConfig.getDocuments()) {
			if (doc.getId() != null) {
				docById.put(doc.getId(), doc);
			}
		}

		// Validation: Checks that engine is not changed along the hierarchy of documents
		for (Document doc : extractorConfig.getDocuments()) {
			// All parents should either declare the same engine or no engine
			checkParentEngine(deriveEngineName(doc), doc.getInherits());
		}

	}

	private void checkParentEngine(String docEngine, Document parent) {
		if (parent == null)
			return;
		Validate.isTrue(StringUtils.isEmpty(parent.getEngine()) || parent.getEngine().equals(docEngine),
				"Engine can not be changed along the hierarchy: " + parent.getId());
		checkParentEngine(docEngine, parent.getInherits());
	}

	/**
	 * Extracts parts of the given content based on the defined extract-to rules in the config file. It uses the first
	 * matching document with the given url as the document that defines those extract-to rules.
	 * 
	 * @return a map of field names to the extracted value for that field according to the last last extract-to rule
	 *         that matches the field name. If no document matches the given url, null will be returned.
	 */
	public List<ExtractedDoc> extract(Content content) throws Exception {
		Validate.notNull(content);

		// 1. Decide on which document matches the url and contentType
		Document document = findMatchingDoc(content);
		if (document == null) {
			return null;
		}

		// 2. Select an engine for parsing the document
		String engine = deriveEngineName(document);
		Evaluator<? extends EvaluationContext> evalEngine = EvaluatorFactory.getInstance().getEvaluator(engine);
		if (evalEngine == null)
			throw new IllegalArgumentException("No engine found with the name " + engine);

		// 3. Parse the document and start the extraction process
		EvaluationContext context = evalEngine.createContext(content);
		return document.extract(context);
	}

	private String deriveEngineName(Document document) {
		return StringUtils.defaultIfEmpty(document.getInheritedEngine(), extractorConfig.getDefaultEngine());
	}

	private Document findMatchingDoc(Content content) throws Exception {
		for (Document doc : extractorConfig.getDocuments()) {
			if (doc.matches(content.getUrl().toString(), content.getType()))
				return doc;
		}
		return null;
	}

	public Document getDocById(String id) {
		return docById.get(id);
	}

}