package org.sorcerers.git2neo

import org.neo4j.graphdb.RelationshipType

/**
 * @author vovak
 * @since 18/11/16
 */
private enum class NodeRelation {
    PARENT, CONTAINS
}