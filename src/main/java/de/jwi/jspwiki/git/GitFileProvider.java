package de.jwi.jspwiki.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.WikiProvider;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.providers.FileSystemProvider;
import org.apache.wiki.providers.WikiPageProvider;
import org.apache.wiki.search.QueryItem;
import org.apache.wiki.util.TextUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class GitFileProvider implements WikiPageProvider
{
	protected File pageDirectory;

	protected GitController gitController;

	public static final String PROP_PAGEDIR = "jspwiki.gitFileProvider.pageDir";

	public static final String DEFAULT_ENCODING = "UTF-8";

    public static final String EXT = "txt";
	
	protected String encoding;

	private static final Logger log = Logger.getLogger(GitFileProvider.class);

	protected WikiEngine engine;
	protected Properties properties;

	public void initialize(WikiEngine engine, Properties properties)
			throws NoRequiredPropertyException, IOException, FileNotFoundException
	{
		this.engine = engine;
		this.properties = properties;

		encoding = properties.getProperty(WikiEngine.PROP_ENCODING, DEFAULT_ENCODING);

		String pageDirectoryName = TextUtil.getRequiredProperty(properties, PROP_PAGEDIR);
		pageDirectory = new File(pageDirectoryName);

		gitController = new GitController(pageDirectory);

		gitController.init();
	}

	private File getPageFile(WikiPage page)
	{
		return new File(pageDirectory, page.getName() + "." + EXT);
	}

	private File getPageFile(String page)
	{
		return new File(pageDirectory, page + "." + EXT);
	}
	
	public void putPageText(WikiPage page, String text) throws ProviderException
	{
		log.debug("putPageText: " + page);

		File f = getPageFile(page);

		try
		{
			FileUtils.write(f, text, encoding);
		} catch (IOException e)
		{
			throw new ProviderException(e.getMessage());
		}

		String message = (String) page.getAttribute(WikiPage.CHANGENOTE);

		try
		{
			gitController.commit(f, message);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public WikiPage getPageInfo(String page, int version) throws ProviderException
	{
		log.debug("getPageInfo: " + page + " " + version);

		List<WikiPage> versions = null;

		File f = getPageFile(page);
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

		File f = getPageFile(pageName);
		
		if (!f.delete())
		{
			throw new ProviderException("could not delete " +f); 
		}

		try
		{
			gitController.commit(pageDirectory, "delete");
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public void movePage(String from, String to) throws ProviderException
	{
		File ffrom = getPageFile(from);
		File fto = getPageFile(to);
		
		boolean b = ffrom.renameTo(fto);
		if (!b)
		{
			throw new ProviderException("Could not rename " + ffrom + " to " + fto);
		}
		
		try
		{
			gitController.commit(pageDirectory, "delete");
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public boolean pageExists(String page, int version)
	{
		log.debug("pageExists: " + page + " " + version);

		if (version == WikiPageProvider.LATEST_VERSION)
		{
			File f = getPageFile(page);
			return f.exists();
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

		File f = getPageFile(page);

		if (version == WikiPageProvider.LATEST_VERSION)
		{
			String s;
			try
			{
				s = FileUtils.readFileToString(f, encoding);
			} catch (IOException e)
			{
				throw new ProviderException(e.getMessage());
			}
			return s;
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		try
		{
			gitController.readHistoryObject(f, version, bos);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}

		String s;
		try
		{
			s = new String(bos.toByteArray(), encoding);
		} catch (UnsupportedEncodingException e)
		{
			throw new ProviderException(e.getMessage());
		}
		return s;
	}

	public List getVersionHistory(String page) throws ProviderException
	{
		log.debug("getVersionHistory: " + page);

		File file = getPageFile(page);

		try
		{
			String fileName = file.getName();
			
			List<GitVersion> gitVersions = gitController.getVersionHistory(fileName);

			List pageVersions = new ArrayList<WikiPage>(gitVersions.size());

			int v = gitVersions.size();

			for (GitVersion gitVersion : gitVersions)
			{
				WikiPage versionPage = new WikiPage(engine, page);
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

	public Collection findPages(QueryItem[] query)
	{
		return null;
	}

	public Collection getAllPages() throws ProviderException
	{
		String[] ext = {EXT};
		boolean recursive = false;
		
		File[] files = pageDirectory.listFiles(new FilenameFilter()
		{
			public boolean accept(File dir, String name)
			{
				return name.endsWith(EXT);
			}
		});
		
		
		List pages = new ArrayList<WikiPage>(files.length);
		
		for (File f : files)
		{
			String name = FilenameUtils.removeExtension(f.getName());
			
			WikiPage page = getPageInfo(name,  WikiPageProvider.LATEST_VERSION );
			pages.add(page);
		}
		
		return pages;
	}

	public Collection getAllChangedSince(Date date)
	{
		return null;
	}

	public int getPageCount() throws ProviderException
	{
		return getAllPages().size();
	}


}
