/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.artifactbrowser;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.util.HashUtil;
import org.apache.excalibur.source.SourceValidity;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.CommunityTreeHelper;
import org.dspace.app.xmlui.utils.DSpaceValidity;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.ReferenceSet;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Map;

/**
 * Display the children (sub-communities and collections) of a community.
 * @author Andrea Schweer
 */
public class CommunityChildren extends AbstractDSpaceTransformer implements CacheableProcessingComponent
{

    /** Cached validity object */
    private DSpaceValidity validity;

    private CommunityTreeHelper helper;
    private static final Message T_head_community_children =
            message("xmlui.ArtifactBrowser.CommunityChildren.head_community_children");
    private int depth;

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

        helper = new CommunityTreeHelper(context, depth, false);
    }

    @Override
    public void addBody(Body body) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException, ProcessingException {
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
        if (!(dso instanceof Community))
        {
            return;
        }

        // Set up the major variables
        Community community = (Community) dso;
        Collection[] collections = community.getCollections();

        if (community.getSubcommunities().length == 0 && collections.length == 0) {
            // there are no children -- just stop
            return;
        }

        // Build the community viewer division.
        Division home = body.addDivision("community-home", "primary repository community");

        Division parent = home.addDivision("community-children", "secondary children");

        ReferenceSet referenceSet = parent.addReferenceSet("community-children-reference",
                ReferenceSet.TYPE_SUMMARY_LIST,null,"hierarchy");

        helper.makeReferenceSet(referenceSet, community);

        if (collections != null && collections.length > 0)
        {
            // Sub collections
            for (Collection collection : collections) {
                referenceSet.addReference(collection);
            }

        }
        referenceSet.setHead(T_head_community_children.parameterize(community.getName()));
    }

    /**
     * Generate the unique caching key.
     * This key must be unique inside the space of this component.
     */
    public Serializable getKey() {
        try {
            DSpaceObject dso = HandleUtil.obtainHandle(objectModel);

            if (dso == null)
            {
                return "0";  // no item, something is wrong
            }

            return HashUtil.hash(dso.getHandle() + "-" + depth);
        }
        catch (SQLException sqle)
        {
            // Ignore all errors and just return that the component is not cachable.
            return "0";
        }
    }


    /**
     * Generate the cache validity object.
     *
     * This validity object includes all sub-communites of the community being viewed
     * (to the appropriate depth) and all sub-collections.
     */
    public SourceValidity getValidity()
    {
        if (this.validity == null)
        {
            try {
                DSpaceObject dso = HandleUtil.obtainHandle(objectModel);

                if (dso == null)
                {
                    return null;
                }

                if (!(dso instanceof Community))
                {
                    return null;

                }

                helper.addToValidity(validity, (Community) dso);

                this.validity = validity.complete();
            }
            catch (Exception e)
            {
                // Ignore all errors and invalidate the cache.
            }

        }
        return this.validity;
    }


    /**
     * Recycle
     */
    public void recycle()
    {
        // Clear out our item's cache.
        this.validity = null;
        helper.reset();
        super.recycle();
    }
}
