package org.voyanttools.trombone.tool.corpus;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.voyanttools.trombone.model.DocumentCollocate;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.tool.corpus.CorpusCreator;
import org.voyanttools.trombone.tool.corpus.DocumentCollocates;
import org.voyanttools.trombone.util.FlexibleParameters;
import org.voyanttools.trombone.util.TestHelper;

public class DocumentCollocatesTest {

	@Test
	public void test() throws IOException {
		for (Storage storage : TestHelper.getDefaultTestStorages()) {
			System.out.println("Testing with "+storage.getClass().getSimpleName()+": "+storage.getLuceneManager().getClass().getSimpleName());
			test(storage);
		}
	}

	public void test(Storage storage) throws IOException {
		// add the testing file to the storage
		FlexibleParameters parameters = new FlexibleParameters(new String[]{"file="+TestHelper.getResource("udhr/udhr-en.txt")});
		CorpusCreator creator = new CorpusCreator(storage, parameters);
		creator.run();
		parameters.setParameter("corpus", creator.getStoredId());
		parameters.setParameter("query", "human");
		
		
		DocumentCollocates documentCollocates;
		List<DocumentCollocate> documentCollocatesList;
		DocumentCollocate documentCollocate;
		
		// run with default sort contextDocumentRelativeDifferenceDescending
		documentCollocates = new DocumentCollocates(storage, parameters);
		documentCollocates.run();
		documentCollocatesList = documentCollocates.getDocumentCollocates();
		documentCollocate = documentCollocatesList.get(0);
		assertEquals("should", documentCollocate.getTerm());

		// run with terms sort
		parameters.setParameter("sort", "termAsc");
		documentCollocates = new DocumentCollocates(storage, parameters);
		documentCollocates.run();
		documentCollocatesList = documentCollocates.getDocumentCollocates();
		documentCollocate = documentCollocatesList.get(documentCollocatesList.size()-1);
		assertEquals(documentCollocate.getTerm(), "world");
		
		// run with terms relative frequency of context terms sort
		parameters.setParameter("sort", "relDesc");
		documentCollocates = new DocumentCollocates(storage, parameters);
		documentCollocates.run();
		documentCollocatesList = documentCollocates.getDocumentCollocates();
		documentCollocate = documentCollocatesList.get(0);
		assertEquals("should", documentCollocate.getTerm());
		documentCollocate = documentCollocatesList.get(documentCollocatesList.size()-1);
		assertEquals("world", documentCollocate.getTerm());
		
		// run with terms relative frequency of context terms sort
		parameters.setParameter("sort", "rawDesc");
		documentCollocates = new DocumentCollocates(storage, parameters);
		documentCollocates.run();
		documentCollocatesList = documentCollocates.getDocumentCollocates();
		documentCollocate = documentCollocatesList.get(0);
		assertEquals("should", documentCollocate.getTerm());
		documentCollocate = documentCollocatesList.get(documentCollocatesList.size()-1);
		assertEquals("world", documentCollocate.getTerm());

		// run with terms relative frequency of context terms sort
		parameters.setParameter("sort", DocumentCollocate.Sort.docRelDesc.name());
		documentCollocates = new DocumentCollocates(storage, parameters);
		documentCollocates.run();
		documentCollocatesList = documentCollocates.getDocumentCollocates();
		documentCollocate = documentCollocatesList.get(0);
		assertEquals("the", documentCollocate.getTerm());
		documentCollocate = documentCollocatesList.get(documentCollocatesList.size()-1);
		assertEquals("world", documentCollocate.getTerm());
		
		// run with terms relative frequency of context terms sort
		parameters.setParameter("sort", "docRawDesc");
		documentCollocates = new DocumentCollocates(storage, parameters);
		documentCollocates.run();
		documentCollocatesList = documentCollocates.getDocumentCollocates();
		documentCollocate = documentCollocatesList.get(0);
		assertEquals("the", documentCollocate.getTerm());
		documentCollocate = documentCollocatesList.get(documentCollocatesList.size()-1);
		assertEquals("world", documentCollocate.getTerm());
		
		storage.destroy();
	}

}
