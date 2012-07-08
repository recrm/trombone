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
package org.voyanttools.trombone.input.source;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.voyanttools.trombone.document.Metadata;

/**
 * An {@link InputSource} associated with a (transient) {@link InputStream}
 * (such as when reading from an archive or compressed file).
 * 
 * @author "Stéfan Sinclair"
 */
public class InputStreamInputSource implements InputSource {

	/**
	 * the (transient) input stream associated with this input source
	 */
	private InputStream inputStream;

	/**
	 * the id (hash) for this input source
	 */
	private String id;

	/**
	 * the metadata for this input source
	 */
	private Metadata metadata;

	/**
	 * Create a new instance with all of the needed information.
	 * 
	 * @param id
	 *            the ID (should be a relatively short alphanumeric hash code)
	 *            for the input source as generated by
	 *            {@link DigestUtils#md5(String)}
	 * @param metadata
	 *            the metadata associated with the input source (this should
	 *            include information like {@link Source}, location and last
	 *            modified)
	 * @param inputStream
	 *            the (transient) {@link InputStream} associated with this input
	 *            source (which the caller should take care to close when
	 *            finished)
	 */
	public InputStreamInputSource(String id, Metadata metadata,
			InputStream inputStream) {
		this.id = id;
		this.metadata = metadata;
		this.inputStream = inputStream;
	}

	public InputStream getInputStream() throws IOException {
		return this.inputStream;
	}

	public Metadata getMetadata() {
		return this.metadata;
	}

	public String getUniqueId() {
		return this.id;
	}

}
