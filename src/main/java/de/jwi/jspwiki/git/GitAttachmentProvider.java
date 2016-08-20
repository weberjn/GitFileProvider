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
		File f = new File(attachmentDirectory, pagename);
		return f;
	}

	public void putAttachmentData(Attachment attachment, InputStream data) throws ProviderException, IOException
	{
		File dir = getAttachmentDir(attachment);
		
		if (!dir.exists())
		{
			dir.mkdirs();
		}

		
		File f = new File(dir, attachment.getFileName());

		FileUtils.copyInputStreamToFile(data, f);

		try
		{
			gitController.commit(dir, "added attachment");
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public InputStream getAttachmentData(Attachment attachment) throws ProviderException, IOException
	{
		File dir = getAttachmentDir(attachment);
		File f = new File(dir, attachment.getFileName());

		InputStream is = FileUtils.openInputStream(f);

		return is;
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
			Attachment attachment = getAttachmentInfo(page, f.getName(),WikiProvider.LATEST_VERSION);
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
			String pagename = d.getName();

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
		
        Attachment attachment = new Attachment( engine, page.getName(), name);

        List<Attachment> versions;
		try
		{
			versions = getVersionHistoryExc(attachment);
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
        
        if( version == WikiProvider.LATEST_VERSION )
        {
            return (Attachment)versions.get(0);
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

		File attachmentDir = getAttachmentDir(attachment);

		File file = new File(attachmentDir, attachment.getFileName());

		String fileName = String.format("%s/%s", pageName, file.getName());
		
		List<GitVersion> gitVersions = gitController.getVersionHistory(fileName);

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

		}

		return attachmentVersions;

	}

	public void deleteVersion(Attachment att) throws ProviderException
	{
		// TODO Auto-generated method stub

	}

	public void deleteAttachment(Attachment attachment) throws ProviderException
	{
		File attachmentDir = getAttachmentDir(attachment);

		File f = new File(attachmentDir, attachment.getFileName());

		boolean b = f.delete();
		if (!b)
		{
			throw new ProviderException("could not delete " + attachment.getFileName());
		}

		try
		{
			gitController.commit(attachmentDir, "deleted attachment");
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}
	}

	public void moveAttachmentsForPage(String oldParent, String newParent) throws ProviderException
	{
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
			gitController.commit(attachmentDirectory, "moved attachment");
		} catch (GitException e)
		{
			throw new ProviderException(e.getMessage());
		}

	}

}
