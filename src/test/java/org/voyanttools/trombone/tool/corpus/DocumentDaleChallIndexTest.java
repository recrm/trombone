package org.voyanttools.trombone.tool.corpus;

import org.junit.Test;
import org.voyanttools.trombone.model.DaleChallIndex;
import org.voyanttools.trombone.model.DaleChallIndexTest;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.util.FlexibleParameters;
import org.voyanttools.trombone.util.TestHelper;

import java.io.IOException;
import java.util.List;


public class DocumentDaleChallIndexTest {

    private static final String FILE_PATH_FR = "udhr/udhr-fr.txt";
    private static final double EXPECTED_FR_DaleChall_INDEX = -1;

    private static final String FILE_PATH_EN = "udhr/udhr-en.txt";
    private static final double EXPECTED_EN_DALE_CHALL_INDEX = -1;

    @Test
    public void test() throws IOException {
        for (Storage storage : TestHelper.getDefaultTestStorages()) {
            System.out.println("Testing with "+storage.getClass().getSimpleName()+": "+storage.getLuceneManager().getClass().getSimpleName());

            testWithGivenParameters(storage, new FlexibleParameters(new String[]{"string="+DaleChallIndexTest.TEXT}), DaleChallIndexTest.EXPECTED_DALE_CHALL_INDEX);
            testWithGivenParameters(storage, new FlexibleParameters(new String[]{"file="+TestHelper.getResource(FILE_PATH_FR)}), EXPECTED_FR_DaleChall_INDEX);
            testWithGivenParameters(storage, new FlexibleParameters(new String[]{"file="+TestHelper.getResource(FILE_PATH_EN)}), EXPECTED_EN_DALE_CHALL_INDEX);
        }
    }

    private void testWithGivenParameters(Storage storage, FlexibleParameters parameters, double expectedDaleChallIndex) throws IOException {
        CorpusCreator creator = new CorpusCreator(storage, parameters);
        creator.run();

        DocumentDaleChallIndex documentDaleChallIndex = new DocumentDaleChallIndex(storage, parameters);
        documentDaleChallIndex.run();

        List<DaleChallIndex> daleChallIndexes = documentDaleChallIndex.getDaleChallIndexes();

        for (DaleChallIndex daleChallIndex : daleChallIndexes) {
            assert daleChallIndex.getDaleChallIndex() == expectedDaleChallIndex;
        }
    }
}
