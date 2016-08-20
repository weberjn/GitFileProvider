package de.jwi.jspwiki.git;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class T
{
	
	public static void main(String[] args) throws IOException, NoHeadException, GitAPIException
	{
		File f = new File( "../myrepo/" );
		if (!f.exists())
		{
			throw new FileNotFoundException(f.toString());
		}
		
		Git git = Git.open(f);
		
		Repository repository = git.getRepository();
		
		File directory = repository.getDirectory();
		
		System.out.println(git);
		System.out.println(repository);
		System.out.println(directory);
		
		
		Iterable<RevCommit> commits = git.log().call();
		
		Iterator<RevCommit> it = commits.iterator();
		
		while (it.hasNext())
		{
			RevCommit commit = it.next();
			System.out.println( commit.getFullMessage() );
			System.out.println(commit.getAuthorIdent());
			System.out.println(commit.getCommitterIdent());
			System.out.println(commit.getCommitTime());
		}
		
	
		Iterable<RevCommit> logs = git.log().addPath("t.txt").call();
		
		for (RevCommit rev : logs) {
			System.out.println( rev.getFullMessage() );
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
