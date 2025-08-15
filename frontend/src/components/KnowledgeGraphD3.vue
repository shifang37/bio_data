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
      
      <!-- 搜索区域 -->
      <div class="toolbar-center">
        <div class="search-container">
          <el-input
            v-model="searchQuery"
            placeholder="搜索节点（支持多个关键词，用逗号分隔）"
            @keyup.enter="handleSearch"
            clearable
            size="small"
            class="search-input"
          >
            <template #append>
              <el-button @click="handleSearch" size="small">
                <el-icon><Search /></el-icon>
              </el-button>
            </template>
          </el-input>
          <div class="search-options" v-if="isSearchActive">
            <el-button 
              @click="clearSearch" 
              size="small" 
              type="info" 
              plain
            >
              清除搜索
            </el-button>
          </div>
        </div>
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
        
                 <el-button 
           @click="toggleIsolateView" 
           size="small"
           :type="isIsolateView ? 'warning' : 'default'"
           :disabled="!isSearchActive && !selectedNode"
           title="隔离视图：只显示搜索相关的节点和边，或选中节点的邻居"
         >
           {{ isIsolateView ? '退出隔离' : '隔离视图' }}
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
            <!-- 基本信息 -->
            <div class="detail-section">
              <h4 class="section-title">基本信息</h4>
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

            <!-- 节点属性信息 -->
            <div v-if="selectedNode.properties && Object.keys(selectedNode.properties).length > 0" class="detail-section">
              <h4 class="section-title">属性信息</h4>
              <div v-for="(value, key) in selectedNode.properties" :key="key" class="detail-item">
                <span class="detail-label">{{ formatPropertyLabel(key) }}：</span>
                <span class="detail-value">{{ value }}</span>
              </div>
            </div>

            <!-- 无属性信息时的提示 -->
            <div v-else-if="selectedNode.properties && Object.keys(selectedNode.properties).length === 0" class="detail-section">
              <h4 class="section-title">属性信息</h4>
              <div class="no-properties">
                <el-empty description="暂无属性信息" :image-size="60" />
              </div>
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
    <div v-if="loading" class="loading-overlay" v-loading="loading" element-loading-text="加载中...">
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import * as d3 from 'd3'
import { ElEmpty, ElMessage } from 'element-plus'
import { 
  ZoomIn, 
  ZoomOut, 
  Refresh, 
  FullScreen, 
  Close,
  Search 
} from '@element-plus/icons-vue'

export default {
  name: 'KnowledgeGraphD3',
  components: {
    ZoomIn,
    ZoomOut, 
    Refresh,
    FullScreen,
    Close,
    Search,
    ElEmpty
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
    
    // 搜索相关数据
    const searchQuery = ref('')
    const isSearchActive = ref(false)
    const isIsolateView = ref(false)
    const searchResults = ref({
      nodes: [],
      relatedNodes: new Set(),
      relatedLinks: []
    })

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
     let linkTextElements = null

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

     // 格式化属性标签
     const formatPropertyLabel = (key) => {
       const labelMap = {
         // Protein 相关属性
         'function': '功能',
         'location': '位置',
         'molecular_weight': '分子量',
         'expression_tissue': '表达组织',
         
         // Gene 相关属性
         'chromosome': '染色体',
         'exon_count': '外显子数量',
         'expression_pattern': '表达模式',
         'associated_disease': '相关疾病',
         
         // Methylation 相关属性
         'genomic_region': '基因组区域',
         'methylation_effect': '甲基化效应',
         'detection_platform': '检测平台',
         
         // Metabolite 相关属性
         'hmdb_id': 'HMDB ID',
         'formula': '分子式',
         'pathway': '代谢通路',
         'cellular_role': '细胞功能',
         
         // Disease 相关属性
         'disease_type': '疾病类型',
         'severity': '严重程度',
         'prevalence': '患病率',
         
         // Drug 相关属性
         'drug_type': '药物类型',
         'mechanism': '作用机制',
         'target': '作用靶点'
       }
       
       return labelMap[key] || key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())
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

         // 基于度数计算节点大小 - 优化大图显示
     const calculateNodeSize = (degree, maxDegree) => {
       const nodeCount = props.graphData.nodes?.length || 0
       
       // 根据图的大小动态调整节点大小范围
       let minSize, maxSize
       if (nodeCount > 100) {
         // 大图：使用更小的节点尺寸
         minSize = 3
         maxSize = 8
       } else if (nodeCount > 50) {
         // 中等图：适中的节点尺寸
         minSize = 4
         maxSize = 10
       } else {
         // 小图：较大的节点尺寸
         minSize = 5
         maxSize = 12
       }
       
       if (maxDegree === 0) return minSize
       return minSize + (degree / maxDegree) * (maxSize - minSize)
     }

     // 获取边的标签文本
     const getLinkLabel = (link) => {
       // 优先使用 type 字段（JSON文件）
       if (link.type) {
         return link.type
       }
       // 其次使用 relation 字段（CSV文件）
       if (link.relation) {
         return link.relation
       }
       // 最后使用其他可能的字段
       if (link.label) {
         return link.label
       }
       return null
     }

     // 检查边是否与指定节点相关
     const isLinkRelatedToNode = (link, node) => {
       const sourceId = link.source.id || link.source
       const targetId = link.target.id || link.target
       return sourceId === node.id || targetId === node.id
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
        .attr("id", "knowledge-graph-svg")
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
         .on("click", handleBackgroundClick) // 添加背景点击事件

      // 初始化力导向模拟 - 优化参数防止大图拥挤
      const nodeCount = props.graphData.nodes?.length || 0
      const linkCount = props.graphData.links?.length || 0
      
      // 根据图的大小动态调整参数
      const isLargeGraph = nodeCount > 50 || linkCount > 100
      const isMediumGraph = nodeCount > 20 || linkCount > 50
      
      // 动态调整距离参数
      const baseDistance = isLargeGraph ? 120 : isMediumGraph ? 100 : 80
      const linkStrength = isLargeGraph ? 0.6 : isMediumGraph ? 0.7 : 0.8
      
      // 动态调整斥力参数
      const chargeStrength = isLargeGraph ? -200 : isMediumGraph ? -150 : -120
      
      // 动态调整碰撞半径
      const collisionRadius = isLargeGraph ? 8 : isMediumGraph ? 5 : 2
      
      simulation = d3.forceSimulation()
        .force("link", d3.forceLink().id(d => d.id).distance(baseDistance).strength(linkStrength))
        .force("charge", d3.forceManyBody().strength(chargeStrength))
        .force("center", d3.forceCenter(width / 2, height / 2))
        .force("collision", d3.forceCollide().radius(d => d.radius + collisionRadius))
        .alphaTarget(0.01)
        .alphaDecay(isLargeGraph ? 0.03 : 0.02) // 大图使用更慢的衰减
    }

    // 渲染图谱
    const renderGraph = () => {
      if (!svg) return

      loading.value = true

      try {
        // 检查图的大小，决定是否使用简化渲染模式
        const nodeCount = props.graphData.nodes?.length || 0
        const isVeryLargeGraph = nodeCount > 300
        
        if (isVeryLargeGraph) {
          console.log('检测到超大图，启用简化渲染模式')
          // 对于超大图，可以隐藏部分节点标签以提高性能
          // 这里可以添加额外的优化逻辑
        }
        // 清除现有元素
        g.selectAll(".link").remove()
        g.selectAll(".link-text").remove()
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
        linkTextElements = null

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
          .style("stroke", d => d.color || "#999") // 使用CSV中的color字段，如果没有则使用默认颜色
          .style("stroke-opacity", 0.6)
          .style("stroke-width", 1)

                 // 创建边的文本标签（初始时隐藏所有标签）
         linkTextElements = g.selectAll(".link-text")
           .data(links.filter(link => getLinkLabel(link))) // 只对有标签的边创建文本
           .enter()
           .append("text")
           .attr("class", "link-text")
           .style("font-size", "10px")
           .style("fill", "#666")
           .style("pointer-events", "none")
           .style("text-anchor", "middle")
           .style("dominant-baseline", "middle")
           .style("opacity", 0) // 初始时隐藏所有标签
           .text(d => getLinkLabel(d))

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

        // 创建节点圆圈
        nodeElements = nodeGroups
          .append("circle")
          .attr("r", d => d.radius)
          .style("fill", d => getNodeColor(d))
          .style("stroke", d => d.isTopNode ? "#FF4500" : "#2c3e50")
          .style("stroke-width", d => d.isTopNode ? 3 : 1.5)
          .style("cursor", "pointer")

        // 创建节点标签 - 根据图大小优化显示
        const shouldShowAllLabels = nodeCount <= 100
        textElements = nodeGroups
          .append("text")
          .attr("dx", d => d.radius + 3)
          .attr("dy", ".35em")
          .style("font-size", d => d.isTopNode ? "12px" : "10px")
          .style("font-weight", d => d.isTopNode ? "bold" : "normal")
          .style("fill", "#2c3e50")
          .style("pointer-events", "none")
          .style("opacity", d => {
            // 大图只显示重要节点的标签
            if (shouldShowAllLabels) return 1
            return d.isTopNode || d.degree > (maxDegree * 0.5) ? 1 : 0
          })
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

       // 更新边的文本标签位置
       if (linkTextElements && linkTextElements.size() > 0) {
         linkTextElements
           .attr("x", d => (d.source.x + d.target.x) / 2)
           .attr("y", d => (d.source.y + d.target.y) / 2)
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
       // 阻止事件冒泡到背景
       event.stopPropagation()
       
       selectedNode.value = d
       emit('node-click', d)
       
       // 高亮当前节点 - 直接操作所有节点组
       const nodeGroups = g.selectAll(".node")
       if (nodeGroups && nodeGroups.size() > 0) {
         nodeGroups.selectAll("circle")
           .style("stroke-width", n => n.id === d.id ? (n.isTopNode ? 4 : 3) : (n.isTopNode ? 3 : 1.5))
           .style("stroke", n => n.id === d.id ? "#409EFF" : (n.isTopNode ? "#FF4500" : "#2c3e50"))
           .style("fill", n => {
             if (n.id === d.id) return getNodeColor(n) // 当前节点保持原色
             // 检查是否与当前节点相连
             const isConnected = links.some(l => {
               const sourceId = l.source.id || l.source
               const targetId = l.target.id || l.target
               return (sourceId === d.id && targetId === n.id) ||
                      (sourceId === n.id && targetId === d.id)
             })
             return isConnected ? "#FFD700" : getNodeColor(n) // 相连节点高亮为金色
           })
       }
       
       // 高亮相关边
       if (linkElements && linkElements.size() > 0) {
         linkElements
           .style("stroke", l => isLinkRelatedToNode(l, d) ? "#409EFF" : (l.color || "#999"))
           .style("stroke-width", l => isLinkRelatedToNode(l, d) ? 2 : 1)
           .style("stroke-opacity", l => isLinkRelatedToNode(l, d) ? 1 : 0.3)
       }
       
       // 显示与当前节点相关的边的标签
       if (linkTextElements && linkTextElements.size() > 0) {
         linkTextElements
           .style("opacity", l => isLinkRelatedToNode(l, d) ? 1 : 0)
           .style("fill", l => isLinkRelatedToNode(l, d) ? "#409EFF" : "#666")
           .style("font-weight", l => isLinkRelatedToNode(l, d) ? "bold" : "normal")
       }
     }

     // 背景点击事件
     const handleBackgroundClick = (event) => {
       // 如果点击的是背景而不是节点，则隐藏所有边的标签
       if (event.target === svg.node() || event.target === g.node()) {
         selectedNode.value = null
         
         // 恢复所有节点的样式 - 直接操作所有节点组
         const nodeGroups = g.selectAll(".node")
         if (nodeGroups && nodeGroups.size() > 0) {
           nodeGroups.selectAll("circle")
             .style("fill", n => getNodeColor(n))
             .style("stroke-width", n => n.isTopNode ? 3 : 1.5)
             .style("stroke", n => n.isTopNode ? "#FF4500" : "#2c3e50")
         }
         
         // 恢复所有边的样式 - 保持原始颜色
         if (linkElements && linkElements.size() > 0) {
           linkElements
             .style("stroke", d => d.color || "#999")
             .style("stroke-width", 1)
             .style("stroke-opacity", 0.6)
         }
         
         if (linkTextElements && linkTextElements.size() > 0) {
           linkTextElements
             .style("opacity", 0)
             .style("fill", "#666")
             .style("font-weight", "normal")
         }
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

    // 搜索相关方法
    const handleSearch = () => {
      if (!searchQuery.value.trim()) {
        clearSearch()
        return
      }

      // 将查询字符串按中英文逗号、分号分割成多个节点名称
      const queryNodes = searchQuery.value
        .split(/[,;，；]/)
        .map(name => name.trim().toLowerCase())
        .filter(name => name.length > 0)

      if (queryNodes.length === 0) {
        ElMessage.warning('请输入有效的搜索关键词')
        return
      }

      // 查找目标节点
      const targetNodes = props.graphData.nodes.filter(n => 
        queryNodes.some(query => 
          n.name.toLowerCase().includes(query) || 
          n.id.toLowerCase().includes(query) ||
          (n.type && n.type.toLowerCase().includes(query))
        )
      )

      if (targetNodes.length === 0) {
        ElMessage.warning('未找到任何匹配的节点')
        return
      }

      // 获取相关节点和边
      const relatedNodes = new Set()
      const relatedLinks = []
      
      targetNodes.forEach(targetNode => {
        relatedNodes.add(targetNode.id)
        props.graphData.links.forEach(l => {
          const sourceId = l.source.id || l.source
          const targetId = l.target.id || l.target
          if (sourceId === targetNode.id || targetId === targetNode.id) {
            relatedNodes.add(sourceId)
            relatedNodes.add(targetId)
            relatedLinks.push(l)
          }
        })
      })

      // 更新搜索结果
      searchResults.value = {
        nodes: targetNodes,
        relatedNodes,
        relatedLinks
      }
      isSearchActive.value = true
      
      // 清除之前选中的节点，因为现在有搜索活动
      selectedNode.value = null

      // 如果当前是隔离视图，则切换到隔离模式
      if (isIsolateView.value) {
        isolateSearchResults()
      } else {
        // 高亮显示模式：在当前图谱中高亮搜索结果
        highlightSearchResults()
      }

      ElMessage.success(`找到 ${targetNodes.length} 个匹配节点，${relatedNodes.size} 个相关节点`)
    }

    const highlightSearchResults = () => {
      if (!isSearchActive.value || !g) return

      const { nodes: targetNodes, relatedNodes } = searchResults.value

      // 高亮目标节点
      const nodeGroups = g.selectAll(".node")
      if (nodeGroups && nodeGroups.size() > 0) {
        nodeGroups.selectAll("circle")
          .style("stroke-width", n => {
            if (targetNodes.some(t => t.id === n.id)) return 4 // 搜索目标节点
            if (relatedNodes.has(n.id)) return 2 // 相关节点
            return n.isTopNode ? 3 : 1.5 // 其他节点
          })
          .style("stroke", n => {
            if (targetNodes.some(t => t.id === n.id)) return "#FF4500" // 橙红色：搜索目标
            if (relatedNodes.has(n.id)) return "#409EFF" // 蓝色：相关节点
            return n.isTopNode ? "#FF4500" : "#2c3e50" // 默认颜色
          })
          .style("fill", n => {
            if (targetNodes.some(t => t.id === n.id)) return "#FF6B35" // 目标节点填充色
            if (relatedNodes.has(n.id)) return "#87CEEB" // 相关节点填充色
            return getNodeColor(n) // 其他节点保持原色但透明度降低
          })
          .style("opacity", n => {
            return relatedNodes.has(n.id) ? 1 : 0.3 // 非相关节点降低透明度
          })
      }

      // 高亮相关边
      if (linkElements && linkElements.size() > 0) {
        linkElements
          .style("stroke", l => {
            const sourceId = l.source.id || l.source
            const targetId = l.target.id || l.target
            return relatedNodes.has(sourceId) && relatedNodes.has(targetId) ? "#409EFF" : (l.color || "#999")
          })
          .style("stroke-width", l => {
            const sourceId = l.source.id || l.source
            const targetId = l.target.id || l.target
            return relatedNodes.has(sourceId) && relatedNodes.has(targetId) ? 2 : 1
          })
          .style("stroke-opacity", l => {
            const sourceId = l.source.id || l.source
            const targetId = l.target.id || l.target
            return relatedNodes.has(sourceId) && relatedNodes.has(targetId) ? 1 : 0.2
          })
      }

      // 显示相关边的标签
      if (linkTextElements && linkTextElements.size() > 0) {
        linkTextElements
          .style("opacity", l => {
            const sourceId = l.source.id || l.source
            const targetId = l.target.id || l.target
            return relatedNodes.has(sourceId) && relatedNodes.has(targetId) ? 1 : 0
          })
          .style("fill", "#409EFF")
          .style("font-weight", "bold")
      }
    }

    const isolateSearchResults = () => {
      if (!isSearchActive.value) return

      const { relatedNodes, relatedLinks } = searchResults.value
      
      // 创建过滤后的数据
      const filteredNodes = props.graphData.nodes.filter(node => relatedNodes.has(node.id))
      const filteredLinks = props.graphData.links.filter(link => {
        const sourceId = link.source.id || link.source
        const targetId = link.target.id || link.target
        return relatedNodes.has(sourceId) && relatedNodes.has(targetId)
      })

      // 触发过滤变化事件，让父组件知道数据被过滤了
      emit('filter-change', { nodes: filteredNodes, links: filteredLinks })
    }

         // 隔离指定节点及其邻居
     const isolateNodeAndNeighbors = (node) => {
       // 找到与当前节点相连的所有节点
       const relatedNodes = new Set([node.id])
       const relatedLinks = []
       
       // 遍历所有边，找到与当前节点相连的节点和边
       props.graphData.links.forEach(link => {
         const sourceId = link.source.id || link.source
         const targetId = link.target.id || link.target
         
         if (sourceId === node.id) {
           relatedNodes.add(targetId)
           relatedLinks.push(link)
         } else if (targetId === node.id) {
           relatedNodes.add(sourceId)
           relatedLinks.push(link)
         }
       })
       
       // 创建过滤后的数据
       const filteredNodes = props.graphData.nodes.filter(n => relatedNodes.has(n.id))
       const filteredLinks = props.graphData.links.filter(link => {
         const sourceId = link.source.id || link.source
         const targetId = link.target.id || link.target
         return relatedNodes.has(sourceId) && relatedNodes.has(targetId)
       })

       // 触发过滤变化事件
       emit('filter-change', { nodes: filteredNodes, links: filteredLinks })
     }

    // 隔离当前选中的节点及其邻居
    const isolateSelectedNodeAndNeighbors = () => {
      if (!selectedNode.value) {
        ElMessage.warning('请先选择一个节点')
        return
      }
      
      isolateNodeAndNeighbors(selectedNode.value)
    }

         const toggleIsolateView = () => {
       // 优先检查是否有搜索结果，如果有则使用搜索隔离
       if (isSearchActive.value) {
         isIsolateView.value = !isIsolateView.value
         
         if (isIsolateView.value) {
           // 切换到隔离视图
           isolateSearchResults()
         } else {
           // 切换回高亮视图
           // 先恢复完整数据
           emit('filter-change', null)
           // 然后应用高亮
           setTimeout(() => {
             highlightSearchResults()
           }, 50)
         }
       } else if (selectedNode.value) {
         // 如果没有搜索结果但有选中的节点，则隔离该节点及其邻居
         isIsolateView.value = !isIsolateView.value
         
         if (isIsolateView.value) {
           // 切换到隔离视图
           isolateSelectedNodeAndNeighbors()
         } else {
           // 切换回完整视图
           emit('filter-change', null)
         }
       } else {
         ElMessage.warning('请先选择一个节点或进行搜索')
       }
     }

    const clearSearch = () => {
      searchQuery.value = ''
      isSearchActive.value = false
      isIsolateView.value = false
      searchResults.value = {
        nodes: [],
        relatedNodes: new Set(),
        relatedLinks: []
      }

      // 恢复所有节点和边的默认样式
      if (g) {
        const nodeGroups = g.selectAll(".node")
        if (nodeGroups && nodeGroups.size() > 0) {
          nodeGroups.selectAll("circle")
            .style("stroke-width", n => n.isTopNode ? 3 : 1.5)
            .style("stroke", n => n.isTopNode ? "#FF4500" : "#2c3e50")
            .style("fill", n => getNodeColor(n))
            .style("opacity", 1)
        }

        if (linkElements && linkElements.size() > 0) {
          linkElements
            .style("stroke", d => d.color || "#999")
            .style("stroke-width", 1)
            .style("stroke-opacity", 0.6)
        }

        if (linkTextElements && linkTextElements.size() > 0) {
          linkTextElements
            .style("opacity", 0)
            .style("fill", "#666")
            .style("font-weight", "normal")
        }
      }

      // 触发清除过滤事件
      emit('filter-change', null)
      ElMessage.info('已清除搜索结果')
    }

    // 自动优化大图的布局参数
    const optimizeLayoutForGraphSize = () => {
      if (!simulation || !props.graphData.nodes) return
      
      const nodeCount = props.graphData.nodes.length
      const linkCount = props.graphData.links?.length || 0
      
      // 对于超大图（>200节点），进一步优化参数
      if (nodeCount > 200) {
        simulation.force("link").distance(150).strength(0.4)
        simulation.force("charge").strength(-300)
        simulation.force("collision").radius(d => d.radius + 12)
        simulation.alphaDecay(0.04)
        console.log('应用超大图优化参数')
      } else if (nodeCount > 100) {
        simulation.force("link").distance(130).strength(0.5)
        simulation.force("charge").strength(-250)
        simulation.force("collision").radius(d => d.radius + 10)
        simulation.alphaDecay(0.035)
        console.log('应用大图优化参数')
      } else if (nodeCount > 50) {
        simulation.force("link").distance(110).strength(0.6)
        simulation.force("charge").strength(-200)
        simulation.force("collision").radius(d => d.radius + 8)
        simulation.alphaDecay(0.03)
        console.log('应用中图优化参数')
      }
      
      // 重新启动模拟以应用新参数
      simulation.alpha(0.3).restart()
    }

    // 监听数据变化
    watch(() => props.graphData, () => {
      nextTick(() => {
        renderGraph()
        // 自动优化大图的布局参数
        optimizeLayoutForGraphSize()
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
      
      // search related
      searchQuery,
      isSearchActive,
      isIsolateView,

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
      formatPropertyLabel,
      renderGraph,
      
      // search methods
      handleSearch,
      clearSearch,
      toggleIsolateView,
      isolateNodeAndNeighbors,
      isolateSelectedNodeAndNeighbors,
      
      // optimization methods
      optimizeLayoutForGraphSize
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

.toolbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
  margin: 0 20px;
}

.search-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.search-input {
  width: 400px;
  max-width: 100%;
}

.search-options {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
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
   gap: 16px;
 }

 .detail-section {
   border-bottom: 1px solid #f0f0f0;
   padding-bottom: 12px;
 }

 .detail-section:last-child {
   border-bottom: none;
   padding-bottom: 0;
 }

 .section-title {
   margin: 0 0 8px 0;
   color: #303133;
   font-size: 14px;
   font-weight: 600;
   padding-bottom: 4px;
   border-bottom: 1px solid #e4e7ed;
 }

 .no-properties {
   text-align: center;
   padding: 20px 0;
   color: #909399;
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

 :deep(.link-text) {
   user-select: none;
   pointer-events: none;
   font-family: Arial, sans-serif;
   font-weight: 500;
 }
</style>