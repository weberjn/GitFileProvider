/*

	Copyright 2016 Jürgen Weber

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */

package de.jwi.jspwiki.git;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

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

	public void commit(File f, PageMetaData metaData) throws GitException
	{
		String name = f.getName();

		String message = metaData.changenote;

		if (message == null)
		{
			message = "no commit message";
		}

		PersonIdent ident = new PersonIdent(metaData.author, metaData.email, metaData.commitTime, TimeZone.getDefault());

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

	public List<PageMetaData> getVersionHistory(String fileName, boolean readFileSize) throws GitException
	{
		List<PageMetaData> metaDataList = new ArrayList<PageMetaData>();

		try
		{
			Iterator<RevCommit> it = git.log().addPath(fileName).call().iterator();
			while (it.hasNext())
			{
				RevCommit rev = it.next();
				PageMetaData metaData = new PageMetaData();
				metaDataList.add(metaData);

				String changenote = rev.getFullMessage();
				PersonIdent authorIdent = rev.getAuthorIdent();
				int commitTime = rev.getCommitTime(); // seconds since the epoch
				long ms = (long) commitTime * 1000;

				metaData.fileName = fileName;
				metaData.author = authorIdent.getName();
				metaData.changenote = changenote;
				metaData.commitTime = new Date(ms);
				
				if (readFileSize)
				{
					metaData.fileSize = readObjectSize(rev, fileName);
				}
			}

			return metaDataList;
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
			RevCommit revCommit;

			List<RevCommit> commits = new ArrayList<RevCommit>();

			Iterator<RevCommit> it = git.log().addPath(name).call().iterator();
			while (it.hasNext())
			{
				RevCommit r = it.next();
				commits.add(r);
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
