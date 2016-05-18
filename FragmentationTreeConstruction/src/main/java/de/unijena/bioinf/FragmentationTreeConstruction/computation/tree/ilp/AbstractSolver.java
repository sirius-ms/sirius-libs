/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.unijena.bioinf.FragmentationTreeConstruction.computation.tree.ilp;

import com.google.common.collect.BiMap;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.ms.ft.FGraph;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.ft.Fragment;
import de.unijena.bioinf.ChemistryBase.ms.ft.Loss;
import de.unijena.bioinf.FragmentationTreeConstruction.computation.tree.TreeBuilder;
import de.unijena.bioinf.FragmentationTreeConstruction.model.ProcessedInput;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Spectar on 13.11.2014.
 */
abstract public class AbstractSolver {

    public enum SolverState {
        FINISHED,
        SHALL_RETURN_NULL,
        SHALL_BUILD_SOLUTION
    }

    protected boolean built;

    // graph information
    protected final FGraph graph;
    protected final List<Loss> losses;
    protected final int[] edgeIds; // contains variable indices (after 'computeoffsets')
    protected final int[] edgeOffsets; // contains: the first index j of edges starting from a given vertex i

    protected int lastInput;
    protected int secondsPerInstance;
    protected int secondsPerDecomposition;
    protected long timeout;
    protected int numberOfCPUs;

    protected final ProcessedInput input;
    protected final TreeBuilder feasibleSolver;

    // linear program parameters
    protected final double LP_LOWERBOUND;
    protected final int LP_TIMELIMIT;
    protected final int LP_NUM_OF_VARIABLES;
    protected final int LP_NUM_OF_VERTICES;


    ////////////////////////
    //--- CONSTRUCTORS ---//
    ////////////////////////


    /**
     * Minimal constructor
     * - initiate solver with given graph
     * - lower bound will be negative infinity
     * - no maximum computation time will be set
     * @param graph
     */
    public AbstractSolver(FGraph graph)
    {
        this(graph, null, Double.NEGATIVE_INFINITY, null, -1);
    }


    /**
     * optimal constructor
     * - initiate solver with given graph
     * - initiate solver with given lower bound
     * - no maximum computation time will be set
     * @param graph
     * @param lowerbound
     */
    public AbstractSolver(FGraph graph, double lowerbound)
    {
        this(graph, null, lowerbound, null, -1);
    }


    /**
     * Maximum constructor. May be used to ilp_base_construct the correctness of any implemented solver
     *
     * @param graph
     * @param input
     * @param lowerbound
     * @param feasibleSolver
     * @param timeLimit
     */
    protected AbstractSolver(FGraph graph, ProcessedInput input, double lowerbound, TreeBuilder feasibleSolver, int timeLimit)
    {
        if (graph == null) throw new NullPointerException("Cannot solve graph: graph is NULL!");

        this.graph = graph;
        this.losses = new ArrayList<Loss>(graph.numberOfEdges());
        for (Fragment f : graph) {
            for (int k=0; k < f.getInDegree(); ++k) {
                losses.add(f.getIncomingEdge(k));
            }
        }
        this.edgeIds = new int[graph.numberOfEdges()];
        this.edgeOffsets = new int[graph.numberOfVertices()];

        this.LP_LOWERBOUND = lowerbound;
        this.LP_TIMELIMIT = (timeLimit >= 0) ? timeLimit : 0;

        this.input = input;
        this.feasibleSolver = feasibleSolver;

        this.LP_NUM_OF_VERTICES = graph.numberOfVertices();
        this.LP_NUM_OF_VARIABLES = this.losses.size();

        this.built = false;
    }


    /////////////////////
    ///--- METHODS ---///
    /////////////////////

    /**
     * - this class should be implemented through abstract sub methods
     * - model.update() like used within the gurobi solver may be used within one of those, if necessary
     */
    public void prepareSolver() {
        try {
            computeOffsets();
            assert (edgeOffsets != null && (edgeOffsets.length != 0 || losses.size() == 0)) : "Edge edgeOffsets were not calculated?!";

            if (feasibleSolver != null) {
                final FTree presolvedTree = feasibleSolver.buildTree(input, graph, LP_LOWERBOUND);
                defineVariablesWithStartValues(presolvedTree);
            } else {
                defineVariables();
            }

            setConstraints();
            applyLowerBounds();
            setObjective();
            built = true;
        } catch (Exception e) {
            throw new RuntimeException(String.valueOf(e.getMessage()), e);
        }
    }


    /**
     * - edgeOffsets will be used to access edges more efficiently
     * for each constraint i in array 'edgeOffsets': edgeOffsets[i] is the first index, where the constraint i is located
     * (inside 'var' and 'coefs')
     * Additionally, a new loss array will be computed
     */
    final void computeOffsets() {

        for (int k = 1; k < edgeOffsets.length; ++k)
            edgeOffsets[k] = edgeOffsets[k - 1] + graph.getFragmentAt(k - 1).getOutDegree();

        /*
         * for each edge: give it some unique id based on its source vertex id and its offset
         * therefor, the i-th edge of some vertex u will have the id: edgeOffsets[u] + i - 1, if i=1 is the first edge.
         * That way, 'edgeIds' is already sorted by source edge id's! An in O(E) time
          */
        for (int k = 0; k < losses.size(); ++k) {
            final int u = losses.get(k).getSource().getVertexId();
            edgeIds[edgeOffsets[u]++] = k;
        }

        // by using the loop-code above -> edgeOffsets[k] = 2*OutEdgesOf(k), so subtract that 2 away
        for (int k = 0; k < edgeOffsets.length; ++k)
            edgeOffsets[k] -= graph.getFragmentAt(k).getOutDegree();
        //TODO: optimize: edgeOffsets[k] /= 2;
    }


    //-- Methods to initiate the solver
    //-- Exception types may be override within subclasses, if needed

    protected void setConstraints() throws Exception {
        setTreeConstraint();
        setColorConstraint();
        setMinimalTreeSizeConstraint();
    }


    /**
     * Solve the optimal colorful subtree problem, using the chosen solver
     * Need constraints, variables, etc. to be set up
     * @return
     */
    public FTree solve() {
        try {
            if (graph.numberOfEdges() == 1) return buildSolution(graph.getRoot().getOutgoingEdge(0).getWeight(), new boolean[]{true});
            // set up constraints etc.
            prepareSolver();

            // get optimal solution (score) if existing
            AbstractSolver.SolverState signal = solveMIP();
            if (signal == SolverState.SHALL_RETURN_NULL)
                return null;

            // reconstruct tree after having determined the (possible) optimal solution
            final double score = getSolverScore();
            final FTree TREE = buildSolution();
            if (TREE != null && !isComputationCorrect(TREE, this.graph, score))
                throw new RuntimeException("Can't find a feasible solution: Solution is buggy");

            // free any memory, if necessary
            signal = pastBuildSolution();
            if (signal == SolverState.SHALL_RETURN_NULL)
                return null;

            return TREE;
        } catch (Exception e) {
            throw new RuntimeException(String.valueOf(e.getMessage()), e);
        }
    }


    // functions used within 'prepareSolver'

    /**
     * Variables in our problem are the edges of the given graph.
     * In the solution, 0.0 means: edge is not used, while 1.0 means: edge is used
     * @throws Exception
     */
    abstract protected void defineVariables() throws Exception;
    abstract protected void defineVariablesWithStartValues( FTree presolvedTree) throws Exception;

    /**
     * - The sum of all edges kept in the solution (if existing) should be at least as high as the given lower bound
     * - This information might be used by a solver to stop the calculation, when it is obviously not possible to
     *   reach that condition.
     * @throws Exception
     */
    abstract protected void applyLowerBounds() throws Exception;

    /**
     * - relaxed version: for each vertex, there are only one or more outgoing edges,
     *   if there is at least one incomming edge
     *   -> the sum of all incomming edges - sum of outgoing edges >= 0
     * - applying 'ColorConstraint' will tighten this condition to:
     *      for each vertex, there can only be one incommning edge at most and only if one incomming edge is present,
     *      one single outgoing edge can be present.
     * @throws Exception
     */
    abstract protected void setTreeConstraint() throws Exception;

    /**
     * - for each color, take only one incoming edge
     * - the sum of all edges going into color c is equal or less than 1
     * @throws Exception
     */
    abstract protected void setColorConstraint() throws Exception;

    /**
     * - there should be at least one edge leading away from the root
     * @throws Exception
     */
    abstract protected void setMinimalTreeSizeConstraint() throws Exception;

    // functions used within 'solve'

    /**
     * maximize a function z, where z is the sum of edges (as integer) multiplied by their weights
     * thus, this is a MIP problem, where the existence of edges in the solution is to be determined
     * @throws Exception
     */
    abstract protected void setObjective() throws Exception;

    /**
     * - in here, the implemented solver should solve the problem, so that the result can be prepareSolver afterwards
     * - a specific solver might need to set up more before starting the solving process
     * - this is called after all constraints are applied
     * @return
     * @throws Exception
     */
    abstract protected SolverState solveMIP() throws Exception;

    /**
     * - a specific solver might need to do more (or release memory) after the solving process
     * - this is called after the solver() has been executed
     * @return
     * @throws Exception
     */
    abstract protected SolverState pastBuildSolution() throws Exception;

    /**
     * - having found a solution using 'solveMIP' this function shall return a boolean list representing
     *   those edges being kept in the solution.
     * - result[i] == TRUE means the i-th edge is included in the solution, FALSE otherwise
     * @return
     * @throws Exception
     */
    abstract protected boolean[] getVariableAssignment() throws Exception;

    /**
     * - having found a solution using 'solveMIP' this function shall return the score of that solution
     *   (basically, the accumulated weight at the root of the resulting tree or the value of the maximized objective
     *    function, respectively)
     * @return
     * @throws Exception
     */
    abstract protected double getSolverScore() throws Exception;


    protected FTree buildSolution(double score, boolean[] edesAreUsed) throws Exception {
        Fragment graphRoot = null;
        double rootScore = 0d;
        // get root
        {
            int offset = edgeOffsets[graph.getRoot().getVertexId()];
            for (int j = 0; j < graph.getRoot().getOutDegree(); ++j) {
                if (edesAreUsed[edgeIds[offset]]) {
                    final Loss l = losses.get(edgeIds[offset]);
                    graphRoot = l.getTarget();
                    rootScore = l.getWeight();
                    break;
                }
                ++offset;
            }
        }
        assert graphRoot != null;
        if (graphRoot == null) return null;

        final FTree tree = new FTree(graphRoot.getFormula());
        final ArrayDeque<Stackitem> stack = new ArrayDeque<Stackitem>();
        stack.push(new Stackitem(tree.getRoot(), graphRoot));
        while (!stack.isEmpty()) {
            final Stackitem item = stack.pop();
            final int u = item.graphNode.getVertexId();
            int offset = edgeOffsets[u];
            for (int j = 0; j < item.graphNode.getOutDegree(); ++j) {
                if (edesAreUsed[edgeIds[offset]]) {
                    final Loss l = losses.get(edgeIds[offset]);
                    final Fragment child = tree.addFragment(item.treeNode, l.getTarget().getFormula());
                    child.getIncomingEdge().setWeight(l.getWeight());
                    stack.push(new Stackitem(child, l.getTarget()));
                }
                ++offset;
            }
        }
        return tree;
    }

    protected FTree buildSolution() throws Exception {
        final double score = getSolverScore();

        final boolean[] edesAreUsed = getVariableAssignment();
        return buildSolution(score, edesAreUsed);
    }

    ///////////////////////////
    ///--- CLASS-METHODS ---///
    ///////////////////////////

    /**
     * Check, whether or not the given tree 'tree' is the optimal solution for the optimal colorful
     * subtree problem of the given graph 'graph'
     * @param tree
     * @param graph
     * @return
     */
    protected static boolean isComputationCorrect(FTree tree, FGraph graph, double score) {
        final BiMap<Fragment, Fragment> fragmentMap = FTree.createFragmentMapping(tree, graph);
        final Fragment pseudoRoot = graph.getRoot();
        for (Map.Entry<Fragment, Fragment> e : fragmentMap.entrySet()) {
            final Fragment t = e.getKey();
            final Fragment g = e.getValue();
            if (g.getParent() == pseudoRoot) {
                score -= g.getIncomingEdge().getWeight();
            } else {
                if (t.getFormula().isEmpty()) continue;
                final Loss in = t.getIncomingEdge();
                for (int k = 0; k < g.getInDegree(); ++k)
                    if (in.getSource().getFormula().equals(g.getIncomingEdge(k).getSource().getFormula())) {
                        score -= g.getIncomingEdge(k).getWeight();
                    }
            }
        }
        // just trust pseudo edges
        for (Fragment pseudo : tree.getFragments()) {
            if (pseudo.getFormula().isEmpty()) {
                score -= pseudo.getIncomingEdge().getWeight();
            }
        }
        return Math.abs(score) < 1e-9d;
    }


    protected void resetTimeLimit() {
        timeout = System.currentTimeMillis() + secondsPerDecomposition * 1000l;
    }


    protected static class Stackitem {
        protected final Fragment treeNode;
        protected final Fragment graphNode;

        protected Stackitem(Fragment treeNode, Fragment graphNode) {
            this.treeNode = treeNode;
            this.graphNode = graphNode;
        }
    }

}
