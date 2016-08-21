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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.providers.AbstractFileProvider;
import org.apache.wiki.providers.WikiPageProvider;
import org.apache.wiki.util.TextUtil;


public class GitFileProvider extends AbstractFileProvider
{
	protected GitController gitController;

	protected GitUtil gitUtil;
	
	private static final Logger log = Logger.getLogger(GitFileProvider.class);

	protected String m_pageDirectory = "/tmp/";
	
	File pageDirectory;

	public void initialize(WikiEngine engine, Properties properties)
			throws NoRequiredPropertyException, IOException, FileNotFoundException
	{
		super.initialize(engine, properties);

		
		// unfortunately, super keeps its pageDirectory private, so get it again
		
        m_pageDirectory = TextUtil.getCanonicalFilePathProperty(properties, PROP_PAGEDIR,
                System.getProperty("user.home") + File.separator + "jspwiki-files");

        pageDirectory = new File(m_pageDirectory);
        
		gitController = new GitController(pageDirectory);

		gitController.init();
		
		gitUtil = new GitUtil(engine);
	}

	public void putPageText(WikiPage page, String text) throws ProviderException
	{
		log.debug("putPageText: " + page);

		super.putPageText(page, text);
		
		File f = findPage(page.getName());

		GitAttributes gitAttributes = gitUtil.getGitAttributes(page);

		try
		{
			gitController.commit(f, gitAttributes);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public WikiPage getPageInfo(String page, int version) throws ProviderException
	{
		log.debug("getPageInfo: " + page + " " + version);

		List<WikiPage> versions = null;

		File f = findPage(page);
		if (!f.exists())
		{
			return null;
		}

		// always get history, need to see, how many elements there are
		versions = getVersionHistory(page);

		if (version == WikiPageProvider.LATEST_VERSION)
		{
			WikiPage p = versions.get(0);
			return p;
		}

		for (WikiPage p1 : versions)
		{
			if (p1.getVersion() == version)
			{
				return p1;
			}
		}
		return null;
	}

	public void deletePage(String pageName) throws ProviderException
	{
		log.debug("deletePage: " + pageName);

		WikiPage page = getPageInfo(pageName, WikiPageProvider.LATEST_VERSION);

		File f = findPage(pageName);

		if (!f.delete())
		{
			throw new ProviderException("could not delete " + f);
		}

		try
		{
			gitController.commit(pageDirectory, gitUtil.getGitAttributes(page));
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public void movePage(String from, String to) throws ProviderException
	{
		WikiPage page = getPageInfo(from, WikiPageProvider.LATEST_VERSION);
		GitAttributes gitAttributes = gitUtil.getGitAttributes(page);
		
		File ffrom = findPage(from);
		File fto = findPage(to);

		boolean b = ffrom.renameTo(fto);
		if (!b)
		{
			throw new ProviderException("Could not rename " + ffrom + " to " + fto);
		}

		try
		{
			gitController.commit(pageDirectory, gitAttributes);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public boolean pageExists(String page, int version)
	{
		log.debug("pageExists: " + page + " " + version);

		File f = findPage(page);
		if (!f.exists())
		{
			return false;
		}
		
		if (version == WikiPageProvider.LATEST_VERSION)
		{
			return true;
		}

		List versionHistory = null;
		try
		{
			versionHistory = getVersionHistory(page);
		} catch (ProviderException e)
		{
			log.error(e);
		}

		return version <= versionHistory.size();
	}

	public String getPageText(String page, int version) throws ProviderException
	{
		log.debug("getPageText: " + page + " " + version);

		File f = findPage(page);

		if (version == WikiPageProvider.LATEST_VERSION)
		{
			String s;
			try
			{
				s = FileUtils.readFileToString(f, m_encoding);
			} catch (IOException e)
			{
				throw new ProviderException(e.getMessage());
			}
			return s;
		}

		String s;

		InputStream is = null;
		
		try
		{
			is = gitController.readHistoryObject(f.getName(), version);
			
			s = IOUtils.toString(is, m_encoding);
			
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		} catch (IOException e)
		{
			throw new ProviderException(e.getMessage());
		}
		finally {
			if (is != null)
			{
				try
				{
					is.close();
				} catch (IOException e)
				{
					throw new ProviderException(e.getMessage());
				}
			}
		}

		return s;
	}

	public List getVersionHistory(String page) throws ProviderException
	{
		log.debug("getVersionHistory: " + page);

		File file = findPage(page);

		try
		{
			String fileName = file.getName();

			List<GitVersion> gitVersions = gitController.getVersionHistory(fileName, false);

			List<WikiPage> pageVersions = new ArrayList<WikiPage>(gitVersions.size());

			int v = gitVersions.size();

			for (GitVersion gitVersion : gitVersions)
			{
				WikiPage versionPage = new WikiPage(m_engine, page);
				pageVersions.add(versionPage);

				versionPage.setVersion(v--);

				versionPage.setAttribute(WikiPage.CHANGENOTE, gitVersion.changenote);

				versionPage.setAuthor(gitVersion.author);

				versionPage.setLastModified(gitVersion.commitTime);

			}

			return pageVersions;

		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}

	}

	public String getProviderInfo()
	{
		// TODO
		return "";
	}

	public void deleteVersion(String pageName, int version) throws ProviderException
	{
		log.debug("deleteVersion: " + pageName + " " + version);

		throw new ProviderException("not supported");
	}

	public boolean pageExists(String page)
	{
		return pageExists(page, WikiPageProvider.LATEST_VERSION);
	}

}
