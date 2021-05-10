/*******************************************************************************
 * Trombone is a flexible text processing and analysis library used
 * primarily by Voyant Tools (voyant-tools.org).
 * 
 * Copyright (©) 2007-2012 Stéfan Sinclair & Geoffrey Rockwell
 * 
 * This file is part of Trombone.
 * 
 * Trombone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Trombone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Trombone.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.voyanttools.trombone.storage.memory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.voyanttools.trombone.lucene.LuceneManager;
import org.voyanttools.trombone.lucene.SingleIndexLuceneManager;
import org.voyanttools.trombone.nlp.NlpFactory;
import org.voyanttools.trombone.storage.CorpusStorage;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.storage.StoredDocumentSourceStorage;
import org.voyanttools.trombone.storage.file.FileMigrator;

/**
 * An in-memory implementation of the {@link StoredDocumentSourceStorage}. This
 * is typically faster (like for testing) but of course the size of the store is
 * limited by memory and is transient.
 * 
 * @author Stéfan Sinclair
 */
public class MemoryStorage implements Storage {

	private Map<String, Object> storedObjectsMap = new HashMap<String, Object>();
	
	/**
	 * the {@link StoredDocumentSourceStorage} for this storage
	 */
	private StoredDocumentSourceStorage storedDocumentSourceStorage;

	private CorpusStorage corpusStorage;
	
	private LuceneManager luceneManager = null;
	private LuceneManager notebookLuceneManager = null;

	private NlpFactory nlpAnnotatorFactory = new NlpFactory();

	/**
	 * Create a new instance of this class.
	 */
	public MemoryStorage() {
		storedDocumentSourceStorage = new MemoryStoredDocumentSourceStorage();
		corpusStorage = new MemoryCorpusStorage();
	}

	public StoredDocumentSourceStorage getStoredDocumentSourceStorage() {
		return storedDocumentSourceStorage;
	}

	public CorpusStorage getCorpusStorage() {
		return corpusStorage;
	}
	
	public void destroy() throws IOException {
		storedDocumentSourceStorage = new MemoryStoredDocumentSourceStorage();
	}

	@Override
	public LuceneManager getLuceneManager() throws CorruptIndexException, IOException {
		if (luceneManager==null) {
			luceneManager = new SingleIndexLuceneManager(this, new MemoryDirectoryFactory());
		}
		return luceneManager;
	}

	@Override
	public LuceneManager getNotebookLuceneManager() throws IOException {
		if (notebookLuceneManager==null) {
			notebookLuceneManager = new SingleIndexLuceneManager(this, new MemoryDirectoryFactory());
		}
		return notebookLuceneManager;
	}

	@Override
	public String storeString(String string, Location location) {
		String id = DigestUtils.md5Hex(string);
		storeString(string, id, location);
		return id;
	}
	
	@Override
	public void storeString(String string, String id, Location location) {
		storeString(string, id, location, false);
	}

	@Override
	public void storeString(String string, String id, Location location, boolean canOverwrite) {
		if (storedObjectsMap.containsKey("id")==false || canOverwrite) {
			storedObjectsMap.put(id, string);
		}
	}
	
	@Override
	public String retrieveString(String id, Location location) throws IOException {
		Object string = (String) storedObjectsMap.get(id);
		if (string==null) throw new IOException("Unable to find stored string with the ID: "+id);
		if (string instanceof String == false) throw new IOException("An object was stored with this ID but it's not a string: "+id);
		return (String) string;
	}

	@Override
	public void storeStrings(Collection<String> strings, String id, Location location) throws IOException {
		String string = StringUtils.join(strings, "\n");
		storeString(string, id, location);
	}

	@Override
	public String storeStrings(Collection<String> strings, Location location) throws IOException {
		String string = StringUtils.join(strings, "\n");
		return storeString(string, location);
	}
	
	@Override
	public List<String> retrieveStrings(String id, Location location) throws IOException {
		String string = retrieveString(id, location);
		return Arrays.asList(StringUtils.split(string, "\n"));
	}

	@Override
	public boolean hasStoredString(String id, Location location) {
		return storedObjectsMap.containsKey(id);
	}

	@Override
	public boolean isStored(String id, Location location) {
		return storedObjectsMap.containsKey(id);
	}

	@Override
	public String store(Object obj, Location location) throws IOException {
		String id = UUID.randomUUID().toString();
		store(obj, id, location);
		return id;
	}

	@Override
	public void store(Object obj, String id, Location location) throws IOException {
		storedObjectsMap.put(id, obj);
	}

	@Override
	public Object retrieve(String id, Location location) throws IOException,
			ClassNotFoundException {
		return storedObjectsMap.get(id);
	}
	
	@Override
	public Reader getStoreReader(String id, Location location) throws IOException {
		return new StringReader(retrieveString(id, location));
	}

	@Override
	public Writer getStoreWriter(String id, Location location) throws IOException {
		return getStoreWriter(id, location, false);
	}
	
	@Override
	public Writer getStoreWriter(String id, Location location, boolean append) throws IOException {
		return new MemoryStorageStringWriter(id, Storage.Location.cache, append);
	}
	
	private class MemoryStorageStringWriter extends StringWriter {
		private String id;
		private Location location;
		private boolean append;
		private MemoryStorageStringWriter(String id, Location location, boolean append) {
			this.id = id;
			this.location = location;
			this.append = append;
		}
		@Override
		public void close() throws IOException {
			if (append && isStored(id, location)) { // we can append
				storeString(retrieveString(id, location)+this.toString(), id, location);
			} else {
				storeString(this.toString(), id, location);
			}
			super.close();
		}
		
	}

	@Override
	public DB getDB(String id, boolean readOnly) {
		if (!isStored(id, Storage.Location.object)) {
			DB db = DBMaker.newMemoryDB()
					.transactionDisable()
					.closeOnJvmShutdown().make();
			storedObjectsMap.put(id, db);
		}
		return (DB) storedObjectsMap.get(id);
	}
	
	public void closeDB(DB db) {
		// do nothing since we need to keep the engine open for potential future requests
	}
	public boolean existsDB(String id) {
		return storedObjectsMap.containsKey(id);
	}

	@Override
	public FileMigrator getMigrator(String id) throws IOException {
		return null; // not possible to migrate from memory
	}

	@Override
	public NlpFactory getNlpAnnotatorFactory() {
		return nlpAnnotatorFactory;
	}
}
