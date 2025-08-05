<template>
  <div class="knowledge-graph-viewer">
    <!-- 工具栏 -->
    <div class="toolbar" v-if="!isFullscreen">
      <div class="toolbar-left">
        <el-tag type="info" size="small">
          <el-icon><Share /></el-icon>
          网络图谱
        </el-tag>
        
        <el-divider direction="vertical" />
        
        <el-button-group>
          <el-button @click="zoomIn" :icon="ZoomIn" size="small">放大</el-button>
          <el-button @click="zoomOut" :icon="ZoomOut" size="small">缩小</el-button>
          <el-button @click="resetZoom" :icon="Refresh" size="small">重置</el-button>
        </el-button-group>
        
        <el-divider direction="vertical" />
        
        <el-button @click="toggleFullscreen" :icon="FullScreen" size="small">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </el-button>
      </div>
      
      <div class="toolbar-right">
        <el-input
          v-model="searchText"
          placeholder="搜索节点..."
          @input="handleSearch"
          @clear="clearSearch"
          clearable
          size="small"
          style="width: 200px;"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        
        <el-button @click="showFilterDialog = true" :icon="Filter" size="small">
          过滤
        </el-button>
        
        <el-button @click="showStatistics = !showStatistics" :icon="DataAnalysis" size="small">
          统计
        </el-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content" :class="{ 'fullscreen': isFullscreen }">
      <!-- 全屏退出按钮 -->
      <div v-if="isFullscreen" class="fullscreen-exit-button">
        <el-button 
          @click="toggleFullscreen" 
          type="primary" 
          :icon="FullScreen" 
          size="large"
          circle
        >
          退出全屏
        </el-button>
      </div>
      
      <!-- 图谱容器 -->
      <div class="graph-container">
        <div 
          ref="graphContainer" 
          class="graph-canvas"
          :style="{ height: graphHeight + 'px' }"
        ></div>
        
        <!-- 加载状态 -->
        <div v-if="loading" class="loading-overlay">
          <el-loading-spinner />
          <p>正在渲染知识图谱...</p>
        </div>
        
        <!-- 空状态 -->
        <div v-if="!loading && (!graphData.nodes || graphData.nodes.length === 0)" class="empty-state">
          <el-empty description="暂无图谱数据">
            <el-button type="primary" @click="$emit('upload-file')">
              上传文件
            </el-button>
          </el-empty>
        </div>
      </div>
      
      <!-- 侧边栏 -->
      <div v-if="(showStatistics || selectedNode) && !isFullscreen" class="sidebar">
        <!-- 统计信息面板 -->
        <el-card v-if="showStatistics" class="statistics-panel" shadow="never">
          <template #header>
            <div class="card-header">
              <span>图谱统计</span>
              <el-button @click="showStatistics = false" type="text" :icon="Close" size="small" />
            </div>
          </template>
          
          <div class="statistics-content">
            <div class="stat-item">
              <span class="stat-label">节点数量：</span>
              <span class="stat-value">{{ statistics.nodeCount || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">边数量：</span>
              <span class="stat-value">{{ statistics.linkCount || 0 }}</span>
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
            <div v-if="statistics.nodeTypes" class="stat-section">
              <h4>节点类型分布</h4>
              <div v-for="(count, type) in statistics.nodeTypes" :key="type" class="type-item">
                <span class="type-name">{{ type }}：</span>
                <span class="type-count">{{ count }}</span>
              </div>
            </div>
            
            <!-- 关系类型分布 -->
            <div v-if="statistics.relationTypes" class="stat-section">
              <h4>关系类型分布</h4>
              <div v-for="(count, type) in statistics.relationTypes" :key="type" class="type-item">
                <span class="type-name">{{ type }}：</span>
                <span class="type-count">{{ count }}</span>
              </div>
            </div>
          </div>
        </el-card>
        
        <!-- 节点详情面板 -->
        <el-card v-if="selectedNode" class="node-details-panel" shadow="never">
          <template #header>
            <div class="card-header">
              <span>节点详情</span>
              <el-button 
                @click="selectedNode = null" 
                type="text" 
                size="small"
                style="color: #606266; padding: 4px;"
                class="close-button"
              >
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </template>
          
          <div class="node-details-content">
            <div class="detail-item">
              <span class="detail-label">ID：</span>
              <span class="detail-value">{{ selectedNode.id }}</span>
            </div>
            <div class="detail-item" v-if="selectedNode.name">
              <span class="detail-label">名称：</span>
              <span class="detail-value">{{ selectedNode.name }}</span>
            </div>
            <div class="detail-item" v-if="selectedNode.label">
              <span class="detail-label">标签：</span>
              <el-tag size="small">{{ selectedNode.label }}</el-tag>
            </div>
            <div class="detail-item" v-if="selectedNode.type">
              <span class="detail-label">类型：</span>
              <el-tag type="info" size="small">{{ selectedNode.type }}</el-tag>
            </div>
            
            <!-- 属性信息 -->
            <div v-if="selectedNode.properties" class="properties-section">
              <h4>属性信息</h4>
              <div v-for="(value, key) in selectedNode.properties" :key="key" class="property-item">
                <span class="property-key">{{ key }}：</span>
                <span class="property-value">{{ value }}</span>
              </div>
            </div>
            
            <!-- 邻居节点 -->
            <div class="neighbors-section">
              <h4>邻居节点 ({{ getNeighborCount(selectedNode.id) }})</h4>
              <el-button 
                @click="showNeighbors(selectedNode.id)" 
                type="primary" 
                size="small"
                :loading="loadingNeighbors"
              >
                显示邻居
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 过滤对话框 -->
    <el-dialog
      v-model="showFilterDialog"
      title="过滤设置"
      width="500px"
    >
      <el-form :model="filterForm" label-width="100px">
        <el-form-item label="节点类型">
          <el-select
            v-model="filterForm.nodeTypes"
            multiple
            placeholder="选择节点类型"
            style="width: 100%"
          >
            <el-option
              v-for="type in availableNodeTypes"
              :key="type"
              :label="type"
              :value="type"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="关系类型">
          <el-select
            v-model="filterForm.relationTypes"
            multiple
            placeholder="选择关系类型"
            style="width: 100%"
          >
            <el-option
              v-for="type in availableRelationTypes"
              :key="type"
              :label="type"
              :value="type"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="搜索文本">
          <el-input
            v-model="filterForm.searchText"
            placeholder="在节点名称和属性中搜索"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="resetFilter">重置</el-button>
          <el-button @click="showFilterDialog = false">取消</el-button>
          <el-button type="primary" @click="applyFilter">应用</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { 
  Share, ZoomIn, ZoomOut, Refresh, FullScreen, 
  Search, Filter, DataAnalysis, Close 
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { Network } from 'vis-network/standalone/esm/vis-network'

export default {
  name: 'KnowledgeGraphViewer',
  components: {
    Share, ZoomIn, ZoomOut, Refresh, FullScreen,
    Search, Filter, DataAnalysis, Close
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
  emits: ['node-click', 'node-select', 'upload-file', 'filter-change'],
  setup(props, { emit }) {
    // 响应式数据
    const graphContainer = ref(null)
    const loading = ref(false)
    const isFullscreen = ref(false)
    const showStatistics = ref(false)
    const showFilterDialog = ref(false)
    const selectedNode = ref(null)
    const searchText = ref('')
    const loadingNeighbors = ref(false)
    
    // 图谱实例
    let networkInstance = null
    
    // 过滤表单
    const filterForm = reactive({
      nodeTypes: [],
      relationTypes: [],
      searchText: ''
    })
    
    // 计算属性
    const graphHeight = computed(() => {
      return isFullscreen.value ? window.innerHeight : 600
    })
    
    const availableNodeTypes = computed(() => {
      const types = new Set()
      if (props.graphData.nodes) {
        props.graphData.nodes.forEach(node => {
          if (node.type) types.add(node.type)
          if (node.label) types.add(node.label)
        })
      }
      return Array.from(types)
    })
    
    const availableRelationTypes = computed(() => {
      const types = new Set()
      if (props.graphData.links) {
        props.graphData.links.forEach(link => {
          if (link.relation) types.add(link.relation)
          if (link.type) types.add(link.type)
        })
      }
      return Array.from(types)
    })
    
    // 方法
    const zoomIn = () => {
      if (networkInstance) {
        const scale = networkInstance.getScale()
        networkInstance.moveTo({ scale: scale * 1.2 })
      }
    }
    
    const zoomOut = () => {
      if (networkInstance) {
        const scale = networkInstance.getScale()
        networkInstance.moveTo({ scale: scale * 0.8 })
      }
    }
    
    const resetZoom = () => {
      if (networkInstance) {
        networkInstance.fit()
      }
    }
    
    const toggleFullscreen = () => {
      if (!isFullscreen.value) {
        // 进入全屏
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
        // 退出全屏
        if (document.exitFullscreen) {
          document.exitFullscreen()
        } else if (document.webkitExitFullscreen) {
          document.webkitExitFullscreen()
        } else if (document.msExitFullscreen) {
          document.msExitFullscreen()
        }
      }
    }
    
    // 监听全屏状态变化
    const handleFullscreenChange = () => {
      isFullscreen.value = !!(
        document.fullscreenElement ||
        document.webkitFullscreenElement ||
        document.msFullscreenElement
      )
      
      nextTick(() => {
        if (isFullscreen.value) {
          renderGraph()
        } else {
          // 退出全屏时重新渲染以适应新尺寸
          setTimeout(() => {
            renderGraph()
          }, 100)
        }
      })
    }
    
    const handleSearch = (value) => {
      // 实现搜索逻辑
      if (!value) {
        clearSearch()
        return
      }
      
      // 高亮匹配的节点
      highlightNodes(value)
    }
    
    const clearSearch = () => {
      // 清除搜索高亮
      if (networkInstance) {
        networkInstance.selectNodes([])
      }
    }
    
    const highlightNodes = (searchTerm) => {
      if (!props.graphData.nodes || !networkInstance) return
      
      const matchingNodeIds = props.graphData.nodes
        .filter(node => {
          const name = node.name || ''
          const id = node.id || ''
          return name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                 id.toLowerCase().includes(searchTerm.toLowerCase())
        })
        .map(node => node.id)
      
      networkInstance.selectNodes(matchingNodeIds)
      
      if (matchingNodeIds.length > 0) {
        networkInstance.focus(matchingNodeIds[0], { animation: true })
      }
    }
    
    const getNeighborCount = (nodeId) => {
      if (!props.graphData.links) return 0
      
      return props.graphData.links.filter(link => 
        link.source === nodeId || link.target === nodeId
      ).length
    }
    
    const showNeighbors = async (nodeId) => {
      loadingNeighbors.value = true
      try {
        // 这里可以调用API获取邻居节点
        // 暂时使用本地数据
        const neighbors = getLocalNeighbors(nodeId)
        
        // 高亮邻居节点
        if (networkInstance) {
          const neighborIds = [nodeId, ...neighbors.map(n => n.id)]
          networkInstance.selectNodes(neighborIds)
        }
        
        ElMessage.success(`找到 ${neighbors.length} 个邻居节点`)
      } catch (error) {
        ElMessage.error('获取邻居节点失败: ' + error.message)
      } finally {
        loadingNeighbors.value = false
      }
    }
    
    const getLocalNeighbors = (nodeId) => {
      if (!props.graphData.links || !props.graphData.nodes) return []
      
      const neighborIds = new Set()
      props.graphData.links.forEach(link => {
        if (link.source === nodeId) {
          neighborIds.add(link.target)
        } else if (link.target === nodeId) {
          neighborIds.add(link.source)
        }
      })
      
      return props.graphData.nodes.filter(node => neighborIds.has(node.id))
    }
    
    const applyFilter = () => {
      emit('filter-change', { ...filterForm })
      showFilterDialog.value = false
    }
    
    const resetFilter = () => {
      filterForm.nodeTypes = []
      filterForm.relationTypes = []
      filterForm.searchText = ''
    }

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
      const totalEdges = props.graphData.links.length * 2 // 无向图每条边贡献2个度数
      return (totalEdges / nodeCount).toFixed(1)
    }
    
    // 渲染图谱
    const renderGraph = async () => {
      if (!graphContainer.value || !props.graphData.nodes) return
      
      loading.value = true
      
      try {
        // 清理现有实例
        if (networkInstance) {
          networkInstance.destroy()
          networkInstance = null
        }
        
        // 清空容器
        graphContainer.value.innerHTML = ''
        
        await renderNetworkGraph()
      } catch (error) {
        console.error('渲染图谱失败:', error)
        ElMessage.error('渲染图谱失败: ' + error.message)
      } finally {
        loading.value = false
      }
    }
    
    // 计算节点度数和中心性指标
    const calculateNodeMetrics = (nodes, links) => {
      // 计算度数
      const degreeMap = new Map()
      links.forEach(link => {
        const sourceId = link.source
        const targetId = link.target
        degreeMap.set(sourceId, (degreeMap.get(sourceId) || 0) + 1)
        degreeMap.set(targetId, (degreeMap.get(targetId) || 0) + 1)
      })

      // 计算最大度数用于归一化
      const degrees = Array.from(degreeMap.values())
      const maxDegree = degrees.length > 0 ? Math.max(...degrees) : 1

      return { degreeMap, maxDegree }
    }

    // 基于度数计算节点大小
    const calculateNodeSize = (degree, maxDegree) => {
      const minSize = 12
      const maxSize = 35
      if (maxDegree === 0) return minSize
      return minSize + (degree / maxDegree) * (maxSize - minSize)
    }

    // 计算介度中心性（基于Brandes算法的简化版本）
    const calculateBetweennessCentrality = (nodes, links) => {
      const betweenness = new Map(nodes.map(node => [node.id, 0]))
      const nodeCount = nodes.length
      
      if (nodeCount <= 2) return betweenness
      
      // 构建邻接表
      const adjacencyList = new Map()
      nodes.forEach(node => adjacencyList.set(node.id, []))
      links.forEach(link => {
        adjacencyList.get(link.source).push(link.target)
        adjacencyList.get(link.target).push(link.source)
      })

      // 对每个节点作为起点计算最短路径
      nodes.forEach(source => {
        const stack = []
        const predecessors = new Map(nodes.map(node => [node.id, []]))
        const distance = new Map(nodes.map(node => [node.id, -1]))
        const sigma = new Map(nodes.map(node => [node.id, 0]))
        
        distance.set(source.id, 0)
        sigma.set(source.id, 1)
        const queue = [source.id]
        
        // BFS
        while (queue.length > 0) {
          const v = queue.shift()
          stack.push(v)
          
          adjacencyList.get(v).forEach(w => {
            if (distance.get(w) < 0) {
              queue.push(w)
              distance.set(w, distance.get(v) + 1)
            }
            if (distance.get(w) === distance.get(v) + 1) {
              sigma.set(w, sigma.get(w) + sigma.get(v))
              predecessors.get(w).push(v)
            }
          })
        }
        
        // 累积依赖
        const delta = new Map(nodes.map(node => [node.id, 0]))
        while (stack.length > 0) {
          const w = stack.pop()
          predecessors.get(w).forEach(v => {
            delta.set(v, delta.get(v) + (sigma.get(v) / sigma.get(w)) * (1 + delta.get(w)))
          })
          if (w !== source.id) {
            betweenness.set(w, betweenness.get(w) + delta.get(w))
          }
        }
      })
      
      return betweenness
    }

    // 使用 vis-network 渲染网络图
    const renderNetworkGraph = async () => {
      // 计算节点度数
      const { degreeMap, maxDegree } = calculateNodeMetrics(props.graphData.nodes, props.graphData.links)
      
      // 计算介度中心性
      const betweennessMap = calculateBetweennessCentrality(props.graphData.nodes, props.graphData.links)
      const betweennessValues = Array.from(betweennessMap.values())
      const maxBetweenness = betweennessValues.length > 0 ? Math.max(...betweennessValues) : 1

      const nodes = props.graphData.nodes.map(node => {
        const degree = degreeMap.get(node.id) || 0
        const betweenness = betweennessMap.get(node.id) || 0
        const normalizedBetweenness = maxBetweenness > 0 ? (betweenness / maxBetweenness * 100).toFixed(1) : 0
        const nodeSize = calculateNodeSize(degree, maxDegree)
        const isHighDegree = degree > maxDegree * 0.7
        const isTopNode = degree > maxDegree * 0.8 || betweenness > maxBetweenness * 0.7

        return {
          id: node.id,
          label: node.name || node.id,
          title: `${node.name || node.id}\n类型: ${node.type || node.label || '未知'}\n连接数: ${degree}\n中心性: ${normalizedBetweenness}%\n重要度: ${isTopNode ? '高' : isHighDegree ? '中' : '低'}`,
          color: {
            background: getNodeColor(node),
            border: isTopNode ? '#FF4500' : '#2c3e50',
            highlight: {
              background: getNodeColor(node),
              border: '#409EFF'
            }
          },
          size: nodeSize,
          degree: degree,
          font: { 
            size: isHighDegree ? 16 : 14,
            color: '#2c3e50',
            strokeWidth: isTopNode ? 2 : 0,
            strokeColor: '#ffffff'
          },
          borderWidth: isTopNode ? 4 : 2,
          borderWidthSelected: 5,
          shadow: isTopNode ? {
            enabled: true,
            color: 'rgba(255, 69, 0, 0.3)',
            size: 8,
            x: 2,
            y: 2
          } : false
        }
      })
      
      const edges = props.graphData.links.map((link, index) => ({
        id: index,
        from: link.source,
        to: link.target,
        label: link.relation || link.type || '',
        title: link.relation || link.type || '',
        arrows: 'to',
        color: getLinkColor(link),
        smooth: {
          type: 'continuous',
          roundness: 0.2
        },
        width: 1.5
      }))
      
      const data = { nodes, edges }
      
      const options = {
        physics: {
          enabled: true,
          solver: 'forceAtlas2Based',
          forceAtlas2Based: {
            gravitationalConstant: -80, // 增强节点间排斥力
            centralGravity: 0.005, // 减少中心重力
            springLength: 150, // 缩短连线长度，让图更紧凑
            springConstant: 0.12, // 增强弹簧力
            damping: 0.6, // 增加阻尼，更快稳定
            avoidOverlap: 1.2 // 增强重叠避免
          },
          stabilization: { 
            enabled: true,
            iterations: 300, // 增加迭代次数获得更好布局
            updateInterval: 25, // 更频繁的更新
            onlyDynamicEdges: false,
            fit: true
          },
          adaptiveTimestep: true,
          timestep: 0.3, // 减小时间步长，提高精度
          maxVelocity: 30, // 限制最大速度
          minVelocity: 0.75 // 设置最小速度阈值
        },
        layout: {
          improvedLayout: true,
          clusterThreshold: 150
        },
        interaction: {
          hover: true,
          selectConnectedEdges: false,
          tooltipDelay: 200,
          zoomView: true,
          dragView: true
        },
        nodes: {
          shape: 'dot',
          size: 15,
          font: {
            size: 14,
            color: '#333'
          },
          borderWidth: 2,
          shadow: true,
          scaling: {
            min: 10,
            max: 30,
            label: {
              enabled: true,
              min: 14,
              max: 30
            }
          }
        },
        edges: {
          width: 2,
          color: {
            inherit: 'from'
          },
          smooth: {
            enabled: true,
            type: 'dynamic',
            roundness: 0.5
          },
          arrows: {
            to: {
              enabled: true,
              scaleFactor: 1,
              type: 'arrow'
            }
          },
          shadow: true
        }
      }
      
      networkInstance = new Network(graphContainer.value, data, options)
      
      // 添加事件监听
      networkInstance.on('click', (params) => {
        if (params.nodes.length > 0) {
          const nodeId = params.nodes[0]
          const node = props.graphData.nodes.find(n => n.id === nodeId)
          if (node) {
            selectedNode.value = node
            emit('node-click', node)
          }
        }
      })
      
      networkInstance.on('hoverNode', (params) => {
        networkInstance.canvas.body.container.style.cursor = 'pointer'
      })
      
      networkInstance.on('blurNode', (params) => {
        networkInstance.canvas.body.container.style.cursor = 'default'
      })
      
      // 监听稳定化完成事件，设置为仅在拖拽时启用物理引擎
      networkInstance.on('stabilizationIterationsDone', () => {
        console.log('图谱布局已稳定，设置为拖拽时启用物理引擎')
        networkInstance.setOptions({ 
          physics: {
            enabled: false,
            solver: 'forceAtlas2Based',
            forceAtlas2Based: {
              gravitationalConstant: -50,
              centralGravity: 0.01,
              springLength: 200,
              springConstant: 0.08,
              damping: 0.4,
              avoidOverlap: 1
            }
          }
        })
      })
      
      // 监听拖拽开始事件，启用物理引擎
      networkInstance.on('dragStart', (params) => {
        if (params.nodes.length > 0) {
          console.log('开始拖拽节点，启用物理引擎')
          networkInstance.setOptions({ 
            physics: {
              enabled: true,
              solver: 'forceAtlas2Based'
            }
          })
        }
      })
      
      // 监听拖拽结束事件，延迟关闭物理引擎
      networkInstance.on('dragEnd', (params) => {
        if (params.nodes.length > 0) {
          console.log('拖拽结束，延迟关闭物理引擎')
          // 延迟2秒后关闭物理引擎，让布局稳定
          setTimeout(() => {
            networkInstance.setOptions({ physics: false })
            console.log('物理引擎已关闭')
          }, 2000)
        }
      })
    }
    

    
    // 获取节点颜色
    const getNodeColor = (node) => {
      const colorMap = {
        'Protein': '#ff6b6b',
        'Gene': '#4ecdc4',
        'Methylation': '#45b7d1',
        'Metabolite': '#96ceb4',
        'Drug': '#feca57',
        'Disease': '#ff9ff3'
      }
      
      return colorMap[node.type] || colorMap[node.label] || '#95a5a6'
    }
    
    // 获取节点大小
    const getNodeSize = (node) => {
      // 根据节点的连接数或其他属性确定大小
      return 15
    }
    
    // 获取连线颜色
    const getLinkColor = (link) => {
      const colorMap = {
        'encodes': '#3498db',
        'regulates': '#e74c3c',
        'interacts_with': '#f39c12',
        'targets': '#9b59b6',
        'treats': '#2ecc71'
      }
      
      return colorMap[link.relation] || colorMap[link.type] || '#bdc3c7'
    }
    
    // 监听数据变化
    watch(() => props.graphData, () => {
      nextTick(() => {
        renderGraph()
      })
    }, { deep: true })
    
    // 监听窗口大小变化
    const handleResize = () => {
      if (networkInstance) {
        networkInstance.redraw()
        networkInstance.fit()
      }
    }
    
    // 生命周期
    onMounted(() => {
      window.addEventListener('resize', handleResize)
      
      // 添加全屏状态监听
      document.addEventListener('fullscreenchange', handleFullscreenChange)
      document.addEventListener('webkitfullscreenchange', handleFullscreenChange)
      document.addEventListener('msfullscreenchange', handleFullscreenChange)
      
      nextTick(() => {
        renderGraph()
      })
    })
    
    onUnmounted(() => {
      window.removeEventListener('resize', handleResize)
      
      // 移除全屏状态监听
      document.removeEventListener('fullscreenchange', handleFullscreenChange)
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
      document.removeEventListener('msfullscreenchange', handleFullscreenChange)
      
      if (networkInstance) {
        networkInstance.destroy()
      }
    })
    
    return {
      // refs
      graphContainer,
      loading,
      isFullscreen,
      showStatistics,
      showFilterDialog,
      selectedNode,
      searchText,
      loadingNeighbors,
      filterForm,
      
      // computed
      graphHeight,
      availableNodeTypes,
      availableRelationTypes,
      
      // methods
      zoomIn,
      zoomOut,
      resetZoom,
      toggleFullscreen,
      handleSearch,
      clearSearch,
      getNeighborCount,
      showNeighbors,
      applyFilter,
      resetFilter,
      calculateNetworkDensity,
      calculateAverageDegree,
      renderGraph
    }
  }
}
</script>

<style scoped>
.knowledge-graph-viewer {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
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
}

.main-content.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background: white;
}

.graph-container {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.graph-canvas {
  width: 100%;
  border: 1px solid #e9ecef;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.empty-state {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.sidebar {
  width: 300px;
  background: #f8f9fa;
  border-left: 1px solid #e9ecef;
  overflow-y: auto;
}

.statistics-panel,
.node-details-panel {
  margin: 0;
  border-radius: 0;
  border: none;
  border-bottom: 1px solid #e9ecef;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.statistics-content {
  padding: 0;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.stat-label {
  color: #666;
}

.stat-value {
  font-weight: bold;
  color: #333;
}

.stat-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e9ecef;
}

.stat-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #333;
}

.type-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}

.type-name {
  color: #666;
}

.type-count {
  color: #333;
  font-weight: 500;
}

.node-details-content {
  padding: 0;
}

.detail-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.detail-label {
  color: #666;
  min-width: 60px;
  font-size: 13px;
}

.detail-value {
  color: #333;
  font-weight: 500;
  word-break: break-all;
}

.properties-section,
.neighbors-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e9ecef;
}

.properties-section h4,
.neighbors-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #333;
}

.property-item {
  display: flex;
  margin-bottom: 8px;
  font-size: 13px;
}

.property-key {
  color: #666;
  min-width: 80px;
}

.property-value {
  color: #333;
  word-break: break-all;
}

.close-button {
  color: #606266 !important;
  transition: color 0.2s ease;
  border: none;
  background: transparent;
}

.close-button:hover {
  color: #f56c6c !important;
  background: rgba(245, 108, 108, 0.1);
}

.close-button .el-icon {
  font-size: 16px;
}

.dialog-footer {
  text-align: right;
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

.main-content.fullscreen .graph-canvas {
  background: #f5f7fa !important;
  flex: 1;
  width: 100%;
  height: 100%;
}

/* 确保全屏模式下图谱容器占满整个屏幕 */
.main-content.fullscreen .knowledge-graph-viewer {
  height: 100vh;
  width: 100vw;
  background: #f5f7fa;
}

/* 全屏退出按钮样式 */
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

.fullscreen-exit-button .el-button {
  width: 50px;
  height: 50px;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fullscreen-exit-button .el-button:hover {
  transform: scale(1.1);
  transition: transform 0.2s ease;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
    gap: 12px;
  }
  
  .toolbar-left,
  .toolbar-right {
    width: 100%;
    justify-content: center;
  }
  
  .sidebar {
    width: 100%;
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(100%);
    transition: transform 0.3s ease;
  }
  
  .sidebar.show {
    transform: translateX(0);
  }
}
</style>