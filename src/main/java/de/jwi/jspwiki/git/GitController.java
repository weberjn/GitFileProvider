/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */

package de.jwi.jspwiki.git;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;

public class GitController
{
	private File baseDirectory;

	private Git git;

	private Repository repository;

	public GitController(File baseDirectory)
	{
		super();
		this.baseDirectory = baseDirectory;
	}

	public void init() throws IOException
	{
		git = Git.open(baseDirectory);

		repository = git.getRepository();
	}

	public void commit(File f, GitAttributes gitAttributes) throws GitException
	{
		String name = f.getName();

		String message = gitAttributes.changenote;

		if (message == null)
		{
			message = "no commit message";
		}

		PersonIdent ident = new PersonIdent(gitAttributes.author, gitAttributes.email);

		try
		{
			git.add().addFilepattern(name).call();

			git.commit().setMessage(message).setAuthor(ident).setCommitter(ident).call();

		} catch (NoFilepatternException e)
		{
			throw new GitException(e);
		} catch (GitAPIException e)
		{
			throw new GitException(e);
		}
	}

	public List<GitVersion> getVersionHistory(String fileName, boolean readFileSize) throws GitException
	{
		List<GitVersion> versions = new ArrayList<GitVersion>();

		try
		{
			Iterable<RevCommit> logs = git.log().addPath(fileName).call();

			for (RevCommit rev : logs)
			{
				GitVersion gitVersion = new GitVersion();
				versions.add(gitVersion);

				String changenote = rev.getFullMessage();
				PersonIdent authorIdent = rev.getAuthorIdent();
				int commitTime = rev.getCommitTime(); // seconds since the epoch
				long ms = (long) commitTime * 1000;

				gitVersion.fileName = fileName;
				gitVersion.author = authorIdent.getName();
				gitVersion.changenote = changenote;
				gitVersion.commitTime = new Date(ms);
				
				if (readFileSize)
				{
					gitVersion.fileSize = readObjectSize(rev, fileName);
				}
			}

			return versions;
		} catch (Exception e)
		{
			throw new GitException(e);
		} 	}

	private long readObjectSize(RevCommit revCommit, String fileName) throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException
	{
		ObjectReader reader = null;
		
		try
		{
			reader = repository.newObjectReader();

			RevTree tree = revCommit.getTree();

			TreeWalk treewalk = TreeWalk.forPath(reader, fileName, tree);
			// open object
			ObjectLoader loader = reader.open(treewalk.getObjectId(0));
			
			long size = loader.getSize();

			return size;
		} finally
		{
			if (reader != null)
			{
				reader.close();
			}
		}
	}
	
	public InputStream readHistoryObject(String name, int version) throws GitException
	{
		ObjectReader reader = null;
		try
		{
			Iterable<RevCommit> logs = git.log().addPath(name).call();

			RevCommit revCommit;

			List<RevCommit> commits = new ArrayList<RevCommit>();

			for (RevCommit rev : logs)
			{
				commits.add(rev);
			}

			revCommit = commits.get(commits.size() - version);

			reader = repository.newObjectReader();

			RevTree tree = revCommit.getTree();

			TreeWalk treewalk = TreeWalk.forPath(reader, name, tree);
			// open object
			ObjectLoader loader = reader.open(treewalk.getObjectId(0));

			InputStream is = loader.openStream();
			
			return is;
		} catch (Exception e)
		{
			throw new GitException(e);
		} finally
		{
			if (reader != null)
			{
				reader.close();
			}
		}

	}

}
