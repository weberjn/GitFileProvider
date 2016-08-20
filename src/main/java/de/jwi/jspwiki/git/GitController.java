package de.jwi.jspwiki.git;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
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

	public void commit(File f, String message) throws GitException
	{
		String name = f.getName();

		if (message == null)
		{
			message = "no commit message";
		}

		PersonIdent ident = new PersonIdent("Juergen Weber", "juergen@jwi.de");

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

	public List<GitVersion> getVersionHistory(String fileName) throws GitException
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

				gitVersion.author = authorIdent.getName();
				gitVersion.changenote = changenote;
				gitVersion.commitTime = new Date(ms);
			}

			return versions;
		} catch (NoHeadException e)
		{
			throw new GitException(e);
		} catch (GitAPIException e)
		{
			throw new GitException(e);
		}
	}

	public void readHistoryObject(File file, int version, OutputStream os) throws GitException
	{
		String name = file.getName();

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

			loader.copyTo(os);
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
