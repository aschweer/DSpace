/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.utils;

import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.Reference;
import org.dspace.app.xmlui.wing.element.ReferenceSet;
import org.dspace.browse.ItemCountException;
import org.dspace.browse.ItemCounter;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Stack;

/**
 * Helper class to hold methods common to creating community trees, as used in the community list and when showing a community's children.
 * @author Andrea Schweer
 */
public class CommunityTreeHelper {

    public static final int DEFAULT_DEPTH = 999;

    /** Cached version the community / collection hierarchy */
    private CommunityTreeHelper.TreeNode root;
    private Context context;
    private int depth;
    private boolean excludeCollections;


    public CommunityTreeHelper(Context context, int depth, boolean excludeCollections) {
        this.context = context;
        this.depth = depth;
        this.excludeCollections = excludeCollections;
    }

    public CommunityTreeHelper(Context context, boolean excludeCollections) {
        this(context, DEFAULT_DEPTH, excludeCollections);
    }

    private CommunityTreeHelper.TreeNode getRoot(Community parent) throws SQLException {
        if (root == null) {
            if (parent != null) {
                root = buildTree(parent.getSubcommunities());
            } else {
                root = buildTree(Community.findAllTop(context));
            }
        }
        return root;
    }

    /**
     * Recursively build an includeset of the community / collection hierarchy based upon
     * the given NodeTree.
     *
     * @param referenceSet The include set
     * @param node The current node of the hierarchy.
     */
    public void buildReferenceSet(ReferenceSet referenceSet, TreeNode node) throws WingException
    {
        DSpaceObject dso = node.getDSO();

        Reference objectInclude = referenceSet.addReference(dso);

        // Add all the sub-collections;
        java.util.List<TreeNode> collectionNodes = node.getChildrenOfType(Constants.COLLECTION);
        if (collectionNodes != null && collectionNodes.size() > 0)
        {
            ReferenceSet collectionSet = objectInclude.addReferenceSet(ReferenceSet.TYPE_SUMMARY_LIST);

            for (TreeNode collectionNode : collectionNodes)
            {
                collectionSet.addReference(collectionNode.getDSO());
            }
        }

        // Add all the sub-communities
        java.util.List<TreeNode> communityNodes = node.getChildrenOfType(Constants.COMMUNITY);
        if (communityNodes != null && communityNodes.size() > 0)
        {
            ReferenceSet communitySet = objectInclude.addReferenceSet(ReferenceSet.TYPE_SUMMARY_LIST);

            for (TreeNode communityNode : communityNodes)
            {
                buildReferenceSet(communitySet,communityNode);
            }
        }
    }

    /**
     * Recursively build a list of the community / collection hierarchy based upon
     * the given NodeTree.
     *
     * @param contextPath
     * @param list The parent list
     * @param node The current node of the hierarchy.
     */
    public void buildList(String contextPath, List list, TreeNode node) throws WingException
    {
        DSpaceObject dso = node.getDSO();

        String name = null;
        if (dso instanceof Community)
        {
            name = ((Community) dso).getMetadata("name");
        }
        else if (dso instanceof Collection)
        {
            name = ((Collection) dso).getMetadata("name");
        }

        String url = contextPath + "/handle/"+dso.getHandle();
        list.addItem().addHighlight("bold").addXref(url, name);

        List subList = null;

        // Add all the sub-collections;
        java.util.List<TreeNode> collectionNodes = node.getChildrenOfType(Constants.COLLECTION);
        if (collectionNodes != null && collectionNodes.size() > 0)
        {
        	subList = list.addList("sub-list-"+dso.getID());

            for (TreeNode collectionNode : collectionNodes)
            {
                String collectionName = ((Collection) collectionNode.getDSO()).getMetadata("name");
                String collectionUrl = contextPath + "/handle/"+collectionNode.getDSO().getHandle();
                subList.addItemXref(collectionUrl, collectionName);
            }
        }


        // Add all the sub-communities
        java.util.List<TreeNode> communityNodes = node.getChildrenOfType(Constants.COMMUNITY);
        if (communityNodes != null && communityNodes.size() > 0)
        {
        	if (subList == null)
            {
                subList = list.addList("sub-list-" + dso.getID());
            }

            for (TreeNode communityNode : communityNodes)
            {
                buildList(contextPath, subList,communityNode);
            }
        }
    }

    public void addToValidity(DSpaceValidity validity, Community parent) throws SQLException {
        TreeNode root = getRoot(parent);

        Stack<TreeNode> stack = new Stack<TreeNode>();
        stack.push(root);

        while (!stack.empty())
        {
            TreeNode node = stack.pop();

            validity.add(node.getDSO());

            // If we are configured to use collection strengths (i.e. item counts) then include that number in the validity.
            boolean useCache = ConfigurationManager.getBooleanProperty("webui.strengths.cache");
            if (useCache)
            {
                try
                {	//try to determine Collection size (i.e. # of items)

                    int size = new ItemCounter(context).getCount(node.getDSO());
                    validity.add("size:"+size);
                }
                catch(ItemCountException e) { /* ignore */ }
            }


            for (TreeNode child : node.getChildren())
            {
                stack.push(child);
            }
        }

        // Check if we are configured to assume validity.
        String assumeCacheValidity = ConfigurationManager.getProperty("xmlui.community-list.cache");
        if (assumeCacheValidity != null)
        {
            validity.setAssumedValidityDelay(assumeCacheValidity);
        }
    }

    public void makeList(String contextPath, List list, Community parent) throws WingException, SQLException {
        root = getRoot(parent);
        java.util.List<TreeNode> rootNodes = root.getChildrenOfType(Constants.COMMUNITY);

        for (TreeNode node : rootNodes)
        {
            buildList(contextPath, list, node);
        }
    }

    public void makeReferenceSet(ReferenceSet referenceSet, Community parent) throws WingException, SQLException {
        root = getRoot(parent);
        java.util.List<TreeNode> rootNodes = root.getChildrenOfType(Constants.COMMUNITY);

        for (TreeNode node : rootNodes)
        {
            buildReferenceSet(referenceSet, node);
        }
    }

    /**
     * construct a tree structure of communities and collections. The results
     * of this hierarchy are cached so calling it multiple times is acceptable.
     *
     *
     *
     *
     *
     * @param communities The root level communities
     * @return A root level node.
     */
    public TreeNode buildTree(Community[] communities) throws SQLException
    {
        TreeNode newRoot = new TreeNode();

        // Setup for breath-first traversal
        Stack<TreeNode> stack = new Stack<TreeNode>();

        for (Community community : communities)
        {
            stack.push(newRoot.addChild(community));
        }

        while (!stack.empty())
        {
            TreeNode node = stack.pop();

            // Short circuit if we have reached our max depth.
            if (node.getLevel() >= depth)
            {
                continue;
            }

            // Only communities nodes are pushed on the stack.
            Community community = (Community) node.getDSO();

            for (Community subcommunity : community.getSubcommunities())
            {
                stack.push(node.addChild(subcommunity));
            }

            // Add any collections to the document.
            if (!excludeCollections)
            {
                for (Collection collection : community.getCollections())
                {
                    node.addChild(collection);
                }
            }
        }

        return newRoot;
    }

    public void reset() {
        root = null;
    }

    /**
     * Private class to represent the tree structure of communities & collections.
     */
    protected static class TreeNode
    {
        /** The object this node represents */
        private DSpaceObject dso;

        /** The level in the hierarchy that this node is at. */
        private int level;

        /** All children of this node */
        private java.util.List<TreeNode> children = new ArrayList<TreeNode>();

        /**
         * Construct a new root level node
         */
        public TreeNode()
        {
            // Root level node is add the zero level.
            this.level = 0;
        }

        /**
         * @return The DSpaceObject this node represents
         */
        public DSpaceObject getDSO()
        {
            return this.dso;
        }

        /**
         * Add a child DSpaceObject
         *
         * @param dso The child
         * @return A new TreeNode object attached to the tree structure.
         */
        public TreeNode addChild(DSpaceObject dso)
        {
            TreeNode child = new TreeNode();
            child.dso = dso;
            child.level = this.level + 1;
            children.add(child);
            return child;
        }

        /**
         * @return The current level in the hierarchy of this node.
         */
        public int getLevel()
        {
            return this.level;
        }

        /**
         * @return All children
         */
        public java.util.List<TreeNode> getChildren()
        {
            return children;
        }

        /**
         * @return All children of the given @type.
         */
        public java.util.List<TreeNode> getChildrenOfType(int type)
        {
            java.util.List<TreeNode> results = new ArrayList<TreeNode>();
            for (TreeNode node : children)
            {
                if (node.dso.getType() == type)
                {
                    results.add(node);
                }
            }
            return results;
        }
    }
}
