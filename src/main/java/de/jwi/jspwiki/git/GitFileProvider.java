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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
	private File propertiesDir;

	File pageDirectory;

	protected Map<String, List<WikiPage>> versionHistoryCache;

	protected ConcurrentMap<String, Properties> pageProperties;

	public static final String PROPERTIES_DIR = ".properties";

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

		versionHistoryCache = new HashMap<String, List<WikiPage>>();

		pageProperties = new ConcurrentHashMap<String, Properties>();

		propertiesDir = new File(m_pageDirectory, PROPERTIES_DIR);

		if (!propertiesDir.exists())
		{
			boolean b = propertiesDir.mkdirs();

			if (!b)
			{
				throw new IOException("could not create " + propertiesDir);
			}
		}
	}

	public void putPageText(WikiPage page, String text) throws ProviderException
	{
		log.debug("putPageText: " + page);

		super.putPageText(page, text);

		File f = findPage(page.getName());

		PageMetaData metaData = gitUtil.getPageMetaData(page);

		try
		{
			gitController.commit(f, metaData);
			versionHistoryCache.remove(page.getName());

			putPageProperties(page, metaData);

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

		if (version == WikiPageProvider.LATEST_VERSION)
		{
			WikiPage p = getPageInfoFromProperties(page);
			if (p!=null)
			{
				return p;
			}
		}

		versions = getVersionHistory(page);
		
		if (version == WikiPageProvider.LATEST_VERSION)
		{
			return versions.get(0);
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
			gitController.commit(pageDirectory, gitUtil.getPageMetaData(page));
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public void movePage(String from, String to) throws ProviderException
	{
		WikiPage page = getPageInfo(from, WikiPageProvider.LATEST_VERSION);
		PageMetaData metaData = gitUtil.getPageMetaData(page);

		File ffrom = findPage(from);
		File fto = findPage(to);

		boolean b = ffrom.renameTo(fto);
		if (!b)
		{
			throw new ProviderException("Could not rename " + ffrom + " to " + fto);
		}

		try
		{
			gitController.commit(pageDirectory, metaData);
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
		} finally
		{
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
		List<WikiPage> versionHistory = versionHistoryCache.get(page);
		if (versionHistory == null)
		{
			versionHistory = getVersionHistoryFromGit(page);
			versionHistoryCache.put(page, versionHistory);
		}
		return versionHistory;
	}

	public List<WikiPage> getVersionHistoryFromGit(String page) throws ProviderException
	{
		log.debug("getVersionHistory: " + page);

		File file = findPage(page);

		try
		{
			String fileName = file.getName();

			List<PageMetaData> metaDataList = gitController.getVersionHistory(fileName, false);

			List<WikiPage> pageVersions = new ArrayList<WikiPage>(metaDataList.size());

			int v = metaDataList.size();

			for (PageMetaData metaData : metaDataList)
			{
				WikiPage versionPage = new WikiPage(m_engine, page);
				pageVersions.add(versionPage);

				versionPage.setVersion(v--);

				versionPage.setAttribute(WikiPage.CHANGENOTE, metaData.changenote);

				versionPage.setAuthor(metaData.author);

				versionPage.setLastModified(metaData.commitTime);

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

	protected WikiPage getPageInfoFromProperties(String pageName) throws ProviderException
	{
		Properties p = pageProperties.get(pageName);

		if (p == null)
		{
			String name = mangleName(pageName) + ".properties";
			File f = new File(propertiesDir, name);
			
			if (!f.exists())
			{
				return null;
			}

			p = new Properties();
			FileInputStream fis = null;

			try
			{
				fis = new FileInputStream(f);
				p.load(fis);
			} catch (IOException e)
			{
				throw new ProviderException(e.getMessage());
			} finally
			{
				IOUtils.closeQuietly(fis);
			}
		}
		pageProperties.put(pageName, p);

		WikiPage versionPage = new WikiPage(m_engine, pageName);

		versionPage.setVersion(Integer.parseInt(p.getProperty("version")));

		versionPage.setAttribute(WikiPage.CHANGENOTE, p.getProperty(WikiPage.CHANGENOTE));

		versionPage.setAuthor(p.getProperty("author"));

		Long l = Long.valueOf(p.getProperty("time"));

		versionPage.setLastModified(new Date(l));

		return versionPage;
	}

	protected void putPageProperties(WikiPage page, PageMetaData metaData) throws ProviderException
	{
		Properties p = new Properties();

		p.setProperty("author", metaData.author);
		p.setProperty(WikiPage.CHANGENOTE, metaData.changenote);
		p.setProperty("version", "" + metaData.version);
		p.setProperty("time", "" + metaData.commitTime.getTime());

		String name = mangleName(page.getName()) + ".properties";
		File f = new File(propertiesDir, name);
		PrintWriter out = null;
		try
		{
			out = new PrintWriter(f, m_encoding);
			p.store(out, " JSPWiki page properties for " + page.getName() + ". DO NOT MODIFY!");
		} catch (Exception e)
		{
			throw new ProviderException(e.getMessage());
		} finally
		{
			IOUtils.closeQuietly(out);
		}

		pageProperties.put(page.getName(), p);
	}

}
