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
package org.voyanttools.trombone.model;

import java.io.IOException;

import org.voyanttools.trombone.storage.Storage;

import com.thoughtworks.xstream.annotations.XStreamConverter;

/**
 * @author sgs
 *
 */
@XStreamConverter(DocumentConverter.class)
public class IndexedDocument implements DocumentContainer {

	private String id;
	
	private DocumentMetadata metadata = null;
	
	private Storage storage;
	
	/**
	 * 
	 */
	IndexedDocument(Storage storage, String id) {
		this.storage = storage;
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public StoredDocumentSource asStoredDocumentSource() throws IOException {
		return new StoredDocumentSource(getId(), getMetadata());
	}

	public DocumentMetadata getMetadata() throws IOException {
		if (metadata==null) {
			metadata = storage.getStoredDocumentSourceStorage().getStoredDocumentSourceMetadata(getId());
		}
		return metadata;
	}
	
}
