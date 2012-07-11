/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.artifactbrowser;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.util.HashUtil;
import org.apache.excalibur.source.SourceValidity;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.CommunityTreeHelper;
import org.dspace.app.xmlui.utils.DSpaceValidity;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.ReferenceSet;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.LogManager;

import org.xml.sax.SAXException;

/**
 * Display a list of Communities and collections.
 * 
 * This item may be configured so that it will only display to a specific depth,
 * and may include or exclude collections from the tree.
 * 
 * The configuration option available: <depth exclude-collections="true">999</depth>
 * 
 * @author Scott Phillips
 */
public class CommunityBrowser extends AbstractDSpaceTransformer implements CacheableProcessingComponent
{
    private static Logger log = Logger.getLogger(CommunityBrowser.class);

    /** Language Strings */
    public static final Message T_dspace_home =
        message("xmlui.general.dspace_home");
    
    public static final Message T_title =
        message("xmlui.ArtifactBrowser.CommunityBrowser.title");
    
    public static final Message T_trail =
        message("xmlui.ArtifactBrowser.CommunityBrowser.trail");
    
    public static final Message T_head =
        message("xmlui.ArtifactBrowser.CommunityBrowser.head");
    
    public static final Message T_select =
        message("xmlui.ArtifactBrowser.CommunityBrowser.select");
    
    /** Should collections be excluded from the list */
    protected boolean excludeCollections = false;

    /** What depth is the maximum depth of the tree */
    protected int depth;

    /** cached validity object */
    private SourceValidity validity;

    private CommunityTreeHelper helper;

    /**
     * Set the component up, pulling any configuration values from the sitemap
     * parameters.
     */
    public void setup(SourceResolver resolver, Map objectModel, String src,
            Parameters parameters) throws ProcessingException, SAXException,
            IOException
    {
        super.setup(resolver, objectModel, src, parameters);

        depth = parameters.getParameterAsInteger("depth", CommunityTreeHelper.DEFAULT_DEPTH);
        excludeCollections = parameters.getParameterAsBoolean(
                "exclude-collections", false);

        helper = new CommunityTreeHelper(context, depth, excludeCollections);
    }

    /**
     * Generate the unique caching key.
     * This key must be unique inside the space of this component.
     */
    public Serializable getKey()
    {
    	boolean full = ConfigurationManager.getBooleanProperty("xmlui.community-list.render.full", true);
        return HashUtil.hash(depth + "-" + excludeCollections + "-" + (full ? "true" : "false"));
    }

    /**
     * Generate the cache validity object.
     * 
     * The validity object will include a list of all communities 
     * & collection being browsed along with there logo bitstreams.
     */
    public SourceValidity getValidity()
    {
    	if (validity == null)
    	{
	        try {
	            DSpaceValidity validity = new DSpaceValidity();

                helper.addToValidity(validity, null);
	            
	            this.validity = validity.complete();
	        } 
	        catch (SQLException sqle) 
	        {
	            // ignore all errors and return an invalid cache.
	        }
            log.info(LogManager.getHeader(context, "view_community_list", ""));
    	}
    	return this.validity;
    }

    /**
     * Add a page title and trail links.
     */
    public void addPageMeta(PageMeta pageMeta) throws SAXException,
            WingException, UIException, SQLException, IOException,
            AuthorizeException
    {
        // Set the page title
        pageMeta.addMetadata("title").addContent(T_title);

        pageMeta.addTrailLink(contextPath + "/",T_dspace_home);
        pageMeta.addTrail().addContent(T_trail);
    }

    /**
     * Add a community-browser division that includes references to community and
     * collection metadata.
     */
    public void addBody(Body body) throws SAXException, WingException,
            UIException, SQLException, IOException, AuthorizeException
    {
        Division division = body.addDivision("comunity-browser", "primary");
        division.setHead(T_head);
        division.addPara(T_select);

        boolean full = ConfigurationManager.getBooleanProperty("xmlui.community-list.render.full", true);
        
        if (full)
        {
	        ReferenceSet referenceSet = division.addReferenceSet("community-browser",
	                ReferenceSet.TYPE_SUMMARY_LIST,null,"hierarchy");

            helper.makeReferenceSet(referenceSet, null);
        }
        else
        {
        	List list = division.addList("comunity-browser");

            helper.makeList(contextPath, list, null);
        	
        }
    }

    /**
     * recycle
     */
    public void recycle() 
    {
        helper.reset();
        this.validity = null;
        super.recycle();
    }

}
