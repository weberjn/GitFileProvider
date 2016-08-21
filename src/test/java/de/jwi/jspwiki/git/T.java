package de.jwi.jspwiki.git;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class T
{

	public static void main(String[] args) throws IOException, NoHeadException, GitAPIException
	{
		File f = new File( "../myrepo1/" );
		
		Git git = Git.init().setDirectory( f ).call();
		
//		git = Git.open(f);
		
		Repository repository = git.getRepository();
		
		File directory = repository.getDirectory();
		
		System.out.println(git);
		System.out.println(repository);
		System.out.println(directory);
		
		RevWalk walk = new RevWalk(repository);

		if (true)
			return;
		

		
		ObjectId id = ObjectId.fromString("db92fb4ba61b8b4b65b9e8aed987c959c64307e0");
		
		RevCommit commit = walk.parseCommit(id);
		
		RevTree tree = commit.getTree();
		
		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.addTree(tree);
//		treeWalk.setRecursive(false);
		
		treeWalk.setFilter(PathFilter.create("x.txt"));
		if (!treeWalk.next()) {
            throw new IllegalStateException("Did not find expected file x.txt");
		}
		
		ObjectId objectId = treeWalk.getObjectId(0);
		
		git.notesAdd().setMessage("some note message").setObjectId(commit).call();
		
	}

	static void showCommits(Git git) throws NoHeadException, GitAPIException
	{

		Iterable<RevCommit> commits = git.log().call();

		Iterator<RevCommit> it = commits.iterator();

		while (it.hasNext())
		{
			RevCommit commit = it.next();
			System.out.println(commit.getFullMessage());
			System.out.println(commit.getAuthorIdent());
			System.out.println(commit.getCommitterIdent());
			System.out.println(commit.getCommitTime());
		}

		Iterable<RevCommit> logs = git.log().addPath("t.txt").call();

		for (RevCommit rev : logs)
		{
			System.out.println(rev.getFullMessage());
			System.out.println(rev.getAuthorIdent());
			System.out.println(rev.getCommitterIdent());
			System.out.println(rev.getCommitTime());
		}

	}

	static void t(Git git) throws Exception
	{
		Repository repository = git.getRepository();

		File myfile = new File(repository.getDirectory().getParent(), "t.txt");
		myfile.createNewFile();

		String name = myfile.getName();

		git.add().addFilepattern(name).call();

		git.commit().setMessage("Added testfile").call();
	}
}
