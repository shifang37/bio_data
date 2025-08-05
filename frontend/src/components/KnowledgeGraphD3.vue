<template>
  <div class="knowledge-graph-d3">
    <!-- 工具栏 -->
    <div class="toolbar" v-if="!isFullscreen">
      <div class="toolbar-left">
        <el-tag type="info" size="small">
          节点: {{ graphData.nodes?.length || 0 }}
        </el-tag>
        <el-tag type="success" size="small">
          边: {{ graphData.links?.length || 0 }}
        </el-tag>
        <el-tag type="warning" size="small">
          密度: {{ calculateNetworkDensity() }}%
        </el-tag>
      </div>
      
      <div class="toolbar-right">
        <el-button-group size="small">
          <el-button @click="zoomIn" title="放大">
            <el-icon><ZoomIn /></el-icon>
          </el-button>
          <el-button @click="zoomOut" title="缩小">
            <el-icon><ZoomOut /></el-icon>
          </el-button>
          <el-button @click="resetZoom" title="重置视图">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-button-group>
        
        <el-button @click="toggleFullscreen" size="small" type="primary">
          <el-icon><FullScreen /></el-icon>
          全屏
        </el-button>
        
        <el-button @click="showStatistics = !showStatistics" size="small">
          {{ showStatistics ? '隐藏' : '显示' }}统计
        </el-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content" :class="{ fullscreen: isFullscreen }">
      <!-- 图谱容器 -->
      <div class="graph-container" ref="graphContainer">
        <!-- D3.js 将在这里渲染 SVG -->
      </div>

      <!-- 侧边栏 -->
      <div v-if="(showStatistics || selectedNode) && !isFullscreen" class="sidebar">
        <!-- 统计信息面板 -->
        <el-card v-if="showStatistics" class="statistics-panel" shadow="never">
          <template #header>
            <span>网络统计</span>
          </template>
          
          <div class="statistics-content">
            <div class="stat-item">
              <span class="stat-label">节点数量：</span>
              <span class="stat-value">{{ graphData.nodes?.length || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">边数量：</span>
              <span class="stat-value">{{ graphData.links?.length || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">网络密度：</span>
              <span class="stat-value">{{ calculateNetworkDensity() }}%</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">平均度数：</span>
              <span class="stat-value">{{ calculateAverageDegree() }}</span>
            </div>
            
            <!-- 节点类型分布 -->
            <div class="stat-section">
              <h4>节点类型分布</h4>
              <div v-for="(count, type) in nodeTypeStats" :key="type" class="type-item">
                <span class="type-name">{{ type }}：</span>
                <span class="type-count">{{ count }}</span>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 节点详情面板 -->
        <el-card v-if="selectedNode" class="node-details-panel" shadow="never">
          <template #header>
            <div class="node-details-header">
              <span>节点详情</span>
              <el-button
                @click="selectedNode = null"
                type="text"
                size="small"
                class="close-button"
              >
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </template>
          
          <div class="node-details-content">
            <div class="detail-item">
              <span class="detail-label">名称：</span>
              <span class="detail-value">{{ selectedNode.name || selectedNode.id }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">类型：</span>
              <span class="detail-value">{{ selectedNode.type || selectedNode.label || '未知' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">连接数：</span>
              <span class="detail-value">{{ selectedNode.degree || 0 }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">中心性：</span>
              <span class="detail-value">{{ selectedNode.betweenness || 0 }}%</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">重要度：</span>
              <span class="detail-value">{{ selectedNode.importance || '低' }}</span>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 全屏退出按钮 -->
    <div v-if="isFullscreen" class="fullscreen-exit-button">
      <el-button
        @click="toggleFullscreen"
        type="primary"
        size="large"
        circle
      >
        <el-icon><FullScreen /></el-icon>
      </el-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-overlay">
      <el-loading-directive />
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import * as d3 from 'd3'
import { 
  ZoomIn, 
  ZoomOut, 
  Refresh, 
  FullScreen, 
  Close 
} from '@element-plus/icons-vue'

export default {
  name: 'KnowledgeGraphD3',
  components: {
    ZoomIn,
    ZoomOut, 
    Refresh,
    FullScreen,
    Close
  },
  props: {
    graphData: {
      type: Object,
      default: () => ({ nodes: [], links: [] })
    },
    statistics: {
      type: Object,
      default: () => ({})
    }
  },
  emits: ['node-click', 'filter-change'],
  setup(props, { emit }) {
    // 响应式数据
    const graphContainer = ref(null)
    const loading = ref(false)
    const isFullscreen = ref(false)
    const showStatistics = ref(false)
    const selectedNode = ref(null)

    // D3.js 相关变量
    let svg = null
    let g = null
    let simulation = null
    let zoom = null
    let nodes = []
    let links = []
    let nodeElements = null
    let linkElements = null
    let textElements = null

    // 颜色配置
    const nodeColors = {
      'Protein': '#e74c3c',
      'Gene': '#3498db', 
      'Disease': '#f39c12',
      'Drug': '#2ecc71',
      'Metabolite': '#9b59b6',
      'Pathway': '#1abc9c',
      'Methylation': '#e67e22',
      'Other': '#95a5a6'
    }

    // 计算属性
    const graphHeight = computed(() => {
      return isFullscreen.value ? window.innerHeight : 600
    })

    const nodeTypeStats = computed(() => {
      const stats = {}
      if (props.graphData.nodes) {
        props.graphData.nodes.forEach(node => {
          const type = node.type || node.label || 'Other'
          stats[type] = (stats[type] || 0) + 1
        })
      }
      return stats
    })

    // 计算网络密度
    const calculateNetworkDensity = () => {
      if (!props.graphData.nodes || !props.graphData.links) return 0
      const nodeCount = props.graphData.nodes.length
      if (nodeCount <= 1) return 0
      const maxPossibleEdges = nodeCount * (nodeCount - 1) / 2
      const actualEdges = props.graphData.links.length
      return ((actualEdges / maxPossibleEdges) * 100).toFixed(2)
    }

    // 计算平均度数
    const calculateAverageDegree = () => {
      if (!props.graphData.nodes || !props.graphData.links) return 0
      const nodeCount = props.graphData.nodes.length
      if (nodeCount === 0) return 0
      const totalEdges = props.graphData.links.length * 2
      return (totalEdges / nodeCount).toFixed(1)
    }

    // 获取节点颜色
    const getNodeColor = (node) => {
      const type = node.type || node.label || 'Other'
      return nodeColors[type] || nodeColors['Other']
    }

    // 计算节点度数和中心性
    const calculateNodeMetrics = (nodes, links) => {
      // 计算度数
      const degreeMap = new Map()
      links.forEach(link => {
        const sourceId = link.source.id || link.source
        const targetId = link.target.id || link.target
        degreeMap.set(sourceId, (degreeMap.get(sourceId) || 0) + 1)
        degreeMap.set(targetId, (degreeMap.get(targetId) || 0) + 1)
      })

      const degrees = Array.from(degreeMap.values())
      const maxDegree = degrees.length > 0 ? Math.max(...degrees) : 1

      // 计算介度中心性（简化版本，适用于大规模网络）
      const betweennessMap = calculateBetweennessCentrality(nodes, links)
      const betweennessValues = Array.from(betweennessMap.values())
      const maxBetweenness = betweennessValues.length > 0 ? Math.max(...betweennessValues) : 1

      return { degreeMap, maxDegree, betweennessMap, maxBetweenness }
    }

    // 简化的介度中心性计算（适用于大规模网络）
    const calculateBetweennessCentrality = (nodes, links) => {
      const betweenness = new Map(nodes.map(node => [node.id, 0]))
      
      // 对于大规模网络，使用采样方法减少计算量
      const sampleSize = Math.min(nodes.length, 50) // 最多采样50个节点
      const sampledNodes = nodes.slice(0, sampleSize)
      
      // 构建邻接表
      const adjacencyList = new Map()
      nodes.forEach(node => adjacencyList.set(node.id, []))
      links.forEach(link => {
        const sourceId = link.source.id || link.source
        const targetId = link.target.id || link.target
        adjacencyList.get(sourceId).push(targetId)
        adjacencyList.get(targetId).push(sourceId)
      })

      // 简化的中心性计算
      sampledNodes.forEach(source => {
        const visited = new Set()
        const queue = [source.id]
        const distances = new Map([[source.id, 0]])
        
        while (queue.length > 0) {
          const current = queue.shift()
          if (visited.has(current)) continue
          visited.add(current)
          
          const neighbors = adjacencyList.get(current) || []
          neighbors.forEach(neighbor => {
            if (!visited.has(neighbor)) {
              const newDistance = distances.get(current) + 1
              if (!distances.has(neighbor) || distances.get(neighbor) > newDistance) {
                distances.set(neighbor, newDistance)
                queue.push(neighbor)
              }
            }
          })
        }
        
        // 基于距离计算中心性贡献
        distances.forEach((distance, nodeId) => {
          if (nodeId !== source.id && distance > 0) {
            const contribution = 1 / (distance * distance)
            betweenness.set(nodeId, betweenness.get(nodeId) + contribution)
          }
        })
      })
      
      return betweenness
    }

    // 基于度数计算节点大小
    const calculateNodeSize = (degree, maxDegree) => {
      const minSize = 4
      const maxSize = 12
      if (maxDegree === 0) return minSize
      return minSize + (degree / maxDegree) * (maxSize - minSize)
    }

    // 初始化D3图谱
    const initGraph = () => {
      if (!graphContainer.value) return

      const container = d3.select(graphContainer.value)
      container.selectAll("*").remove() // 清空容器
      
      const width = graphContainer.value.clientWidth
      const height = graphHeight.value

      // 创建SVG
      svg = container
        .append("svg")
        .attr("width", width)
        .attr("height", height)
        .style("background", "#f5f7fa")

      // 创建缩放组
      g = svg.append("g")

      // 初始化缩放
      zoom = d3.zoom()
        .scaleExtent([0.1, 4])
        .on("zoom", (event) => {
          g.attr("transform", event.transform)
        })

      svg.call(zoom)

      // 初始化力导向模拟
      simulation = d3.forceSimulation()
        .force("link", d3.forceLink().id(d => d.id).distance(80).strength(0.8))
        .force("charge", d3.forceManyBody().strength(-120))
        .force("center", d3.forceCenter(width / 2, height / 2))
        .force("collision", d3.forceCollide().radius(d => d.radius + 2))
        .alphaTarget(0.01)
        .alphaDecay(0.02)
    }

    // 渲染图谱
    const renderGraph = () => {
      if (!svg) return

      loading.value = true

      try {
        // 清除现有元素
        g.selectAll(".link").remove()
        g.selectAll(".node").remove()
        g.selectAll(".node-text").remove()

        // 停止当前的模拟
        if (simulation) {
          simulation.stop()
        }

        // 重置变量
        linkElements = null
        nodeElements = null
        textElements = null

        // 检查是否有数据
        if (!props.graphData.nodes || props.graphData.nodes.length === 0) {
          // 数据为空时，清空所有元素并重置选中节点
          nodes = []
          links = []
          selectedNode.value = null
          return
        }

        // 计算节点指标
        const { degreeMap, maxDegree, betweennessMap, maxBetweenness } = 
          calculateNodeMetrics(props.graphData.nodes, props.graphData.links || [])

        // 准备节点数据
        nodes = props.graphData.nodes.map(node => {
          const degree = degreeMap.get(node.id) || 0
          const betweenness = betweennessMap.get(node.id) || 0
          const normalizedBetweenness = maxBetweenness > 0 ? (betweenness / maxBetweenness * 100).toFixed(1) : 0
          const nodeSize = calculateNodeSize(degree, maxDegree)
          const isHighDegree = degree > maxDegree * 0.7
          const isTopNode = degree > maxDegree * 0.8 || betweenness > maxBetweenness * 0.7

          return {
            ...node,
            degree,
            betweenness: normalizedBetweenness,
            radius: nodeSize,
            importance: isTopNode ? '高' : isHighDegree ? '中' : '低',
            isTopNode
          }
        })

        // 准备边数据
        links = (props.graphData.links || []).map(link => ({
          ...link,
          source: link.source.id || link.source,
          target: link.target.id || link.target
        }))

        // 创建边
        linkElements = g.selectAll(".link")
          .data(links)
          .enter()
          .append("line")
          .attr("class", "link")
          .style("stroke", "#999")
          .style("stroke-opacity", 0.6)
          .style("stroke-width", 1)

        // 创建节点组
        const nodeGroups = g.selectAll(".node")
          .data(nodes)
          .enter()
          .append("g")
          .attr("class", "node")
          .call(d3.drag()
            .on("start", dragstarted)
            .on("drag", dragged)
            .on("end", dragended))
          .on("click", handleNodeClick)
          .on("mouseover", handleNodeMouseover)
          .on("mouseout", handleNodeMouseout)

        // 创建节点圆圈
        nodeElements = nodeGroups
          .append("circle")
          .attr("r", d => d.radius)
          .style("fill", d => getNodeColor(d))
          .style("stroke", d => d.isTopNode ? "#FF4500" : "#2c3e50")
          .style("stroke-width", d => d.isTopNode ? 3 : 1.5)
          .style("cursor", "pointer")

        // 创建节点标签
        textElements = nodeGroups
          .append("text")
          .attr("dx", d => d.radius + 3)
          .attr("dy", ".35em")
          .style("font-size", d => d.isTopNode ? "12px" : "10px")
          .style("font-weight", d => d.isTopNode ? "bold" : "normal")
          .style("fill", "#2c3e50")
          .style("pointer-events", "none")
          .text(d => d.name || d.id)

        // 更新力导向模拟
        simulation
          .nodes(nodes)
          .on("tick", ticked)

        simulation.force("link")
          .links(links)

        simulation.alpha(1).restart()

      } catch (error) {
        console.error('渲染图谱失败:', error)
      } finally {
        loading.value = false
      }
    }

    // 力导向模拟tick事件
    const ticked = () => {
      if (linkElements && linkElements.size() > 0) {
        linkElements
          .attr("x1", d => d.source.x)
          .attr("y1", d => d.source.y)
          .attr("x2", d => d.target.x)
          .attr("y2", d => d.target.y)
      }

      if (nodeElements && nodeElements.size() > 0) {
        nodeElements
          .attr("cx", d => d.x)
          .attr("cy", d => d.y)
      }

      if (textElements && textElements.size() > 0) {
        textElements
          .attr("x", d => d.x)
          .attr("y", d => d.y)
      }
    }

    // 拖拽事件处理
    const dragstarted = (event, d) => {
      if (!event.active) simulation.alphaTarget(0.3).restart()
      d.fx = d.x
      d.fy = d.y
    }

    const dragged = (event, d) => {
      d.fx = event.x
      d.fy = event.y
    }

    const dragended = (event, d) => {
      if (!event.active) simulation.alphaTarget(0.01)
      d.fx = null
      d.fy = null
    }

    // 节点交互事件
    const handleNodeClick = (event, d) => {
      selectedNode.value = d
      emit('node-click', d)
    }

    const handleNodeMouseover = (event, d) => {
      // 高亮当前节点和相关边
      d3.select(event.currentTarget)
        .select("circle")
        .style("stroke-width", d.isTopNode ? 4 : 3)
        .style("stroke", "#409EFF")

      // 高亮相关边
      if (linkElements && linkElements.size() > 0) {
        linkElements
          .style("stroke", l => (l.source === d || l.target === d) ? "#409EFF" : "#999")
          .style("stroke-width", l => (l.source === d || l.target === d) ? 2 : 1)
          .style("stroke-opacity", l => (l.source === d || l.target === d) ? 1 : 0.3)
      }
    }

    const handleNodeMouseout = (event, d) => {
      // 恢复节点样式
      d3.select(event.currentTarget)
        .select("circle")
        .style("stroke-width", d.isTopNode ? 3 : 1.5)
        .style("stroke", d.isTopNode ? "#FF4500" : "#2c3e50")

      // 恢复边样式
      if (linkElements && linkElements.size() > 0) {
        linkElements
          .style("stroke", "#999")
          .style("stroke-width", 1)
          .style("stroke-opacity", 0.6)
      }
    }

    // 缩放控制
    const zoomIn = () => {
      if (svg) {
        svg.transition().duration(300).call(
          zoom.scaleBy, 1.5
        )
      }
    }

    const zoomOut = () => {
      if (svg) {
        svg.transition().duration(300).call(
          zoom.scaleBy, 1 / 1.5
        )
      }
    }

    const resetZoom = () => {
      if (svg) {
        svg.transition().duration(500).call(
          zoom.transform,
          d3.zoomIdentity
        )
      }
    }

    // 全屏功能
    const toggleFullscreen = () => {
      if (!isFullscreen.value) {
        if (graphContainer.value) {
          if (graphContainer.value.requestFullscreen) {
            graphContainer.value.requestFullscreen()
          } else if (graphContainer.value.webkitRequestFullscreen) {
            graphContainer.value.webkitRequestFullscreen()
          } else if (graphContainer.value.msRequestFullscreen) {
            graphContainer.value.msRequestFullscreen()
          }
        }
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen()
        } else if (document.webkitExitFullscreen) {
          document.webkitExitFullscreen()
        } else if (document.msExitFullscreen) {
          document.msExitFullscreen()
        }
      }
    }

    const handleFullscreenChange = () => {
      isFullscreen.value = !!(
        document.fullscreenElement ||
        document.webkitFullscreenElement ||
        document.msFullscreenElement
      )

      nextTick(() => {
        if (svg) {
          const width = graphContainer.value.clientWidth
          const height = graphHeight.value
          svg.attr("width", width).attr("height", height)
          simulation.force("center", d3.forceCenter(width / 2, height / 2))
          simulation.alpha(0.3).restart()
        }
      })
    }

    // 窗口大小变化处理
    const handleResize = () => {
      if (svg && graphContainer.value) {
        const width = graphContainer.value.clientWidth
        const height = graphHeight.value
        svg.attr("width", width).attr("height", height)
        simulation.force("center", d3.forceCenter(width / 2, height / 2))
        simulation.alpha(0.1).restart()
      }
    }

    // 监听数据变化
    watch(() => props.graphData, () => {
      nextTick(() => {
        renderGraph()
      })
    }, { deep: true })

    // 生命周期
    onMounted(() => {
      window.addEventListener('resize', handleResize)
      document.addEventListener('fullscreenchange', handleFullscreenChange)
      document.addEventListener('webkitfullscreenchange', handleFullscreenChange)
      document.addEventListener('msfullscreenchange', handleFullscreenChange)

      nextTick(() => {
        initGraph()
        renderGraph()
      })
    })

    onUnmounted(() => {
      window.removeEventListener('resize', handleResize)
      document.removeEventListener('fullscreenchange', handleFullscreenChange)
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
      document.removeEventListener('msfullscreenchange', handleFullscreenChange)

      if (simulation) {
        simulation.stop()
      }
    })

    return {
      // refs
      graphContainer,
      loading,
      isFullscreen,
      showStatistics,
      selectedNode,

      // computed
      graphHeight,
      nodeTypeStats,

      // methods
      zoomIn,
      zoomOut,
      resetZoom,
      toggleFullscreen,
      calculateNetworkDensity,
      calculateAverageDegree,
      renderGraph
    }
  }
}
</script>

<style scoped>
.knowledge-graph-d3 {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.main-content {
  flex: 1;
  display: flex;
  position: relative;
  overflow: hidden;
}

.graph-container {
  flex: 1;
  position: relative;
  background: #f5f7fa;
  overflow: hidden;
}

.sidebar {
  width: 300px;
  background: #ffffff;
  border-left: 1px solid #e9ecef;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.statistics-panel,
.node-details-panel {
  border: none;
}

.statistics-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.stat-label {
  color: #606266;
  font-size: 14px;
}

.stat-value {
  color: #303133;
  font-weight: 600;
  font-size: 14px;
}

.stat-section {
  margin-top: 16px;
}

.stat-section h4 {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 14px;
}

.type-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
  font-size: 12px;
}

.type-name {
  color: #606266;
}

.type-count {
  color: #303133;
  font-weight: 500;
}

.node-details-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.close-button {
  color: #606266 !important;
  transition: color 0.2s ease;
  border: none !important;
  background: transparent !important;
  padding: 4px !important;
}

.close-button:hover {
  color: #f56c6c !important;
  background: rgba(245, 108, 108, 0.1) !important;
}

.node-details-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.detail-label {
  color: #606266;
  font-size: 14px;
}

.detail-value {
  color: #303133;
  font-weight: 500;
  font-size: 14px;
}

/* 全屏模式样式 */
.main-content.fullscreen {
  background: #f5f7fa !important;
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9998;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.main-content.fullscreen .graph-container {
  background: #f5f7fa;
  height: 100vh;
  width: 100vw;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.fullscreen-exit-button {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 9999;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  padding: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(10px);
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

/* D3.js 元素样式 */
:deep(.node) {
  cursor: pointer;
}

:deep(.node circle) {
  transition: stroke-width 0.2s ease, stroke 0.2s ease;
}

:deep(.node text) {
  user-select: none;
  pointer-events: none;
}

:deep(.link) {
  transition: stroke 0.2s ease, stroke-width 0.2s ease, stroke-opacity 0.2s ease;
}
</style>