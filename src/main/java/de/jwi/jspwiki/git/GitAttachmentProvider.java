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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.WikiProvider;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.attachment.Attachment;
import org.apache.wiki.providers.WikiAttachmentProvider;
import org.apache.wiki.search.QueryItem;
import org.apache.wiki.util.TextUtil;
import org.apache.wiki.util.comparators.PageTimeComparator;

public class GitAttachmentProvider implements WikiAttachmentProvider
{
	private static final Logger log = Logger.getLogger(GitFileProvider.class);

	protected File attachmentDirectory;

	protected GitController gitController;

	protected GitUtil gitUtil;

	public static final String PROP_STORAGEDIR = "jspwiki.gitAttachmentProvider.storageDir";

	public static final String GIT_DIR = ".git";

	WikiEngine engine;

	public void initialize(WikiEngine engine, Properties properties) throws NoRequiredPropertyException, IOException
	{
		this.engine = engine;

		String attachmentDirectoryName = TextUtil.getRequiredProperty(properties, PROP_STORAGEDIR);
		attachmentDirectory = new File(attachmentDirectoryName);

		gitController = new GitController(attachmentDirectory);

		gitController.init();

		gitUtil = new GitUtil(engine);
	}

	public String getProviderInfo()
	{
		// TODO Auto-generated method stub
		return null;
	}

	private File getAttachmentDir(Attachment attachment)
	{
		String pagename = attachment.getParentName();
		return getAttachmentDir(pagename);
	}

	private File getAttachmentDir(String pagename)
	{
		String name = TextUtil.urlEncodeUTF8(pagename);
		File f = new File(attachmentDirectory, name);
		return f;
	}

	private File getAttachmentFile(Attachment attachment)
	{
		File dir = getAttachmentDir(attachment);
		String name = TextUtil.urlEncodeUTF8(attachment.getFileName());
		File f = new File(dir, name);
		return f;
	}

	public void putAttachmentData(Attachment attachment, InputStream data) throws ProviderException, IOException
	{
		File dir = getAttachmentDir(attachment);

		if (!dir.exists())
		{
			dir.mkdirs();
		}

		File f = getAttachmentFile(attachment);

		FileUtils.copyInputStreamToFile(data, f);

		GitAttributes gitAttributes = gitUtil.getGitAttributes(attachment);

		try
		{
			gitController.commit(dir, gitAttributes);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public InputStream getAttachmentData(Attachment attachment) throws ProviderException, IOException
	{
		int version = attachment.getVersion();
		
		InputStream is = null;

		File f = getAttachmentFile(attachment);

		if (version == WikiProvider.LATEST_VERSION)
		{
			is = FileUtils.openInputStream(f);

			return is;
		}
		
		try
		{
			String name = String.format("%s/%s",  attachment.getParentName(), attachment.getFileName());
			is = gitController.readHistoryObject(name, version);
			return is;
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public Collection listAttachments(WikiPage page) throws ProviderException
	{
		Collection<Attachment> result = new ArrayList<Attachment>();

		if (page instanceof Attachment)
		{
			return result;
		}

		String pagename = page.getName();

		File dir = getAttachmentDir(pagename);

		if (!dir.exists())
		{
			return result;
		}

		File[] files = dir.listFiles(new FilenameFilter()
		{
			public boolean accept(File dir, String name)
			{
				return !GIT_DIR.equals(name);
			}
		});

		for (File f : files)
		{
			String name = TextUtil.urlDecodeUTF8(f.getName());
			Attachment attachment = getAttachmentInfo(page, name, WikiProvider.LATEST_VERSION);
			result.add(attachment);
		}

		return result;
	}

	public Collection findAttachments(QueryItem[] query)
	{
		return null;
	}

	public List listAllChanged(Date timestamp) throws ProviderException
	{
		File[] directoriesWithAttachments = attachmentDirectory.listFiles(new FilenameFilter()
		{
			public boolean accept(File dir, String name)
			{
				return !GIT_DIR.equals(name);
			}
		});

		ArrayList<Attachment> result = new ArrayList<Attachment>();

		for (File d : directoriesWithAttachments)
		{
			String pagename = TextUtil.urlDecodeUTF8(d.getName());

			Collection c = listAttachments(new WikiPage(engine, pagename));

			for (Iterator it = c.iterator(); it.hasNext();)
			{
				Attachment attachment = (Attachment) it.next();

				if (attachment.getLastModified().after(timestamp))
				{
					result.add(attachment);
				}
			}
		}

		Collections.sort(result, new PageTimeComparator());

		return result;
	}

	public Attachment getAttachmentInfo(WikiPage page, String name, int version) throws ProviderException
	{

		Attachment attachment = new Attachment(engine, page.getName(), name);

		List<Attachment> versions;
		try
		{
			versions = getVersionHistoryExc(attachment);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}

		if (version == WikiProvider.LATEST_VERSION)
		{
			return (Attachment) versions.get(0);
		}

		for (Attachment a : versions)
		{
			if (a.getVersion() == version)
			{
				return a;
			}
		}
		return null;
	}

	public List getVersionHistory(Attachment attachment)
	{
		try
		{
			List versionHistory = getVersionHistoryExc(attachment);
			return versionHistory;
		} catch (GitException e)
		{
			log.error(e);
		}

		return null;
	}

	private List<Attachment> getVersionHistoryExc(Attachment attachment) throws GitException
	{
		log.debug("getVersionHistory: " + attachment);

		String pageName = attachment.getParentName();

		File file = getAttachmentFile(attachment);

		String fileName = String.format("%s/%s", TextUtil.urlEncodeUTF8(pageName),
				TextUtil.urlEncodeUTF8(file.getName()));

		List<GitVersion> gitVersions = gitController.getVersionHistory(fileName, true);

		List<Attachment> attachmentVersions = new ArrayList<Attachment>(gitVersions.size());

		int v = gitVersions.size();

		for (GitVersion gitVersion : gitVersions)
		{
			Attachment attachmentVersion = new Attachment(engine, pageName, attachment.getFileName());

			attachmentVersions.add(attachmentVersion);

			attachmentVersion.setVersion(v--);

			attachmentVersion.setAttribute(WikiPage.CHANGENOTE, gitVersion.changenote);

			attachmentVersion.setAuthor(gitVersion.author);

			attachmentVersion.setLastModified(gitVersion.commitTime);
			
			attachmentVersion.setSize(gitVersion.fileSize);

		}

		return attachmentVersions;

	}

	public void deleteVersion(Attachment att) throws ProviderException
	{
		// TODO Auto-generated method stub

	}

	public void deleteAttachment(Attachment attachment) throws ProviderException
	{
		GitAttributes gitAttributes = gitUtil.getGitAttributes(attachment);

		File attachmentDir = getAttachmentDir(attachment);

		File f = getAttachmentFile(attachment);

		boolean b = f.delete();
		if (!b)
		{
			throw new ProviderException("could not delete " + f.getName());
		}

		try
		{
			gitController.commit(attachmentDir, gitAttributes);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public void moveAttachmentsForPage(String oldParent, String newParent) throws ProviderException
	{
		WikiPage oldpage = engine.getPage(oldParent);

		GitAttributes gitAttributes = gitUtil.getGitAttributes(oldpage);

		File attachmentDirOld = getAttachmentDir(oldParent);
		File attachmentDirNew = getAttachmentDir(newParent);

		File[] files = attachmentDirOld.listFiles();

		try
		{
			for (File f : files)
			{
				FileUtils.moveFileToDirectory(f, attachmentDirNew, false);
			}
		} catch (IOException e)
		{
			throw new ProviderException(e.getMessage());
		}

		try
		{
			gitController.commit(attachmentDirectory, gitAttributes);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}

	}

}
