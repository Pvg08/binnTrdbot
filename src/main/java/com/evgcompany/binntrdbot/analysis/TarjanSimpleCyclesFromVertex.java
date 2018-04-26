/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.GraphTests;

/**
 *
 * @author EVG_adm_T
 */
public class TarjanSimpleCyclesFromVertex<V, E> {
    private Graph<V, E> graph;

    private List<List<V>> cycles;
    private Set<V> marked;
    private ArrayDeque<V> markedStack;
    private ArrayDeque<V> pointStack;
    private Map<V, Integer> vToI;
    private Map<V, Set<V>> removed;

    public TarjanSimpleCyclesFromVertex()
    {
    }

    public TarjanSimpleCyclesFromVertex(Graph<V, E> graph)
    {
        this.graph = GraphTests.requireDirected(graph, "Graph must be directed");
    }

    public Graph<V, E> getGraph()
    {
        return graph;
    }

    public void setGraph(Graph<V, E> graph)
    {
        this.graph = GraphTests.requireDirected(graph, "Graph must be directed");
    }

    public List<List<V>> findSimpleCycles()
    {
        if (graph == null) {
            throw new IllegalArgumentException("Null graph.");
        }
        initState();
        for (V start : graph.vertexSet()) {
            backtrack(start, start);
            while (!markedStack.isEmpty()) {
                marked.remove(markedStack.pop());
            }
        }
        List<List<V>> result = cycles;
        clearState();
        return result;
    }

    public List<List<V>> findSimpleCycles(V start)
    {
        if (graph == null) {
            throw new IllegalArgumentException("Null graph.");
        }
        initState();
        backtrack(start, start);
        while (!markedStack.isEmpty()) {
            marked.remove(markedStack.pop());
        }
        List<List<V>> result = cycles;
        clearState();
        return result;
    }
    
    private boolean backtrack(V start, V vertex)
    {
        boolean foundCycle = false;
        pointStack.push(vertex);
        marked.add(vertex);
        markedStack.push(vertex);

        for (E currentEdge : graph.outgoingEdgesOf(vertex)) {
            V currentVertex = graph.getEdgeTarget(currentEdge);
            if (getRemoved(vertex).contains(currentVertex)) {
                continue;
            }
            int comparison = toI(currentVertex).compareTo(toI(start));
            if (comparison < 0) {
                getRemoved(vertex).add(currentVertex);
            } else if (comparison == 0) {
                foundCycle = true;
                List<V> cycle = new ArrayList<>();
                Iterator<V> it = pointStack.descendingIterator();
                V v;
                while (it.hasNext()) {
                    v = it.next();
                    if (start.equals(v)) {
                        break;
                    }
                }
                cycle.add(start);
                while (it.hasNext()) {
                    cycle.add(it.next());
                }
                cycles.add(cycle);
            } else if (!marked.contains(currentVertex)) {
                boolean gotCycle = backtrack(start, currentVertex);
                foundCycle = foundCycle || gotCycle;
            }
        }

        if (foundCycle) {
            while (!markedStack.peek().equals(vertex)) {
                marked.remove(markedStack.pop());
            }
            marked.remove(markedStack.pop());
        }

        pointStack.pop();
        return foundCycle;
    }

    private void initState()
    {
        cycles = new ArrayList<>();
        marked = new HashSet<>();
        markedStack = new ArrayDeque<>();
        pointStack = new ArrayDeque<>();
        vToI = new HashMap<>();
        removed = new HashMap<>();
        int index = 0;
        for (V v : graph.vertexSet()) {
            vToI.put(v, index++);
        }
    }

    private void clearState()
    {
        cycles = null;
        marked = null;
        markedStack = null;
        pointStack = null;
        vToI = null;
    }

    private Integer toI(V v)
    {
        return vToI.get(v);
    }

    private Set<V> getRemoved(V v)
    {
        Set<V> result = removed.get(v);
        if (result == null) {
            result = new HashSet<>();
            removed.put(v, result);
        }
        return result;
    }
}
