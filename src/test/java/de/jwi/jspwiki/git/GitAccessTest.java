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
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Class for retrieving file history from GIT Inspired by
 * http://wiki.eclipse.org/JGit/User_Guide
 * http://stackoverflow.com/questions/1685228/how-to-cat-a-file-in-jgit
 * http://www.programcreek.com/java-api-examples/index.php?api=org.eclipse.jgit.treewalk.TreeWalk
 */
public class GitAccessTest
{

	/**
	 * @param f,
	 *            the most up2date file which is located in a git-repository
	 * @return the history of the given file
	 */
	public List<File> getHistory(File f) throws Exception
	{
		if (!f.exists())
			throw new Exception("File " + f.getAbsolutePath() + " doesn't exist.");

		List<File> listFiles = new ArrayList<File>();

		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		Repository repository = null;

		// get a manager for the repository
		try
		{
			repository = builder.findGitDir(f).build();

			// windows-workaround for paths
			String sAbsolutePath2File = f.getAbsolutePath().replaceAll("\\", "/");
			String sPathGitDirectory = repository.getDirectory().getParentFile().getAbsolutePath().replaceAll("\\",
					"/");

			// Get relative file path to the file inside the Git-Repository
			String sRelativePath2File = sAbsolutePath2File.replaceFirst(sPathGitDirectory, "");
			// is there still a leading slash?
			if (sRelativePath2File.startsWith("/"))
				// remove it!
				sRelativePath2File = sRelativePath2File.substring(1);

			// Get the central git object from the repository
			Git git = new Git(repository);

			// get logcommand by relative file path
			LogCommand logCommand = git.log().add(git.getRepository().resolve(Constants.HEAD))
					.addPath(sRelativePath2File);

			// get all commits
			for (RevCommit revCommit : logCommand.call())
			{
				// get data
				String sAuthor = revCommit.getAuthorIdent().getName();
				String sComment = revCommit.getFullMessage();
				File fFileRep = getFile(repository, sRelativePath2File, revCommit.getTree());
				Calendar commitDate = new Calendar.Builder().setInstant((long) revCommit.getCommitTime() * 1000)
						.build();

				// set data
				listFiles.add(fFileRep);
			}
		} finally
		{
			repository.close();
		}

		return listFiles;
	}

	/**
	 * retrieve file from git and persist it on local disk
	 * 
	 * @param repository
	 * @param sRelativePath2File
	 * @param tree
	 * @return file from which represents one version in git
	 */
	private File getFile(Repository repository, String sRelativePath2File, RevTree tree) throws Exception
	{
		// use the blob id to read the file's data
		File f = Files.createTempFile(UUID.randomUUID().toString(), ".java").toFile();

		ObjectReader reader = null;

		FileOutputStream fop = new FileOutputStream(f);
		reader = repository.newObjectReader();
		// determine treewalk by relative path
		TreeWalk treewalk = TreeWalk.forPath(reader, sRelativePath2File, tree);
		// open object
		reader.open(treewalk.getObjectId(0)).copyTo(fop);

		return f;
	}

}
