import { reactive } from 'vue'

// 全局知识图谱状态管理
export const knowledgeGraphState = reactive({
  // 当前加载的文件信息
  currentFile: null,
  
  // 图谱数据
  graphData: { nodes: [], links: [] },
  
  // 原始数据副本（用于搜索功能）
  originalGraphData: { nodes: [], links: [] },
  
  // 统计信息
  statistics: {},
  
  // 是否有数据
  hasData: false,
  
  // 设置图谱数据
  setGraphData(data, fileInfo) {
    this.graphData = data
    this.originalGraphData = {
      nodes: [...data.nodes],
      links: [...data.links]
    }
    this.currentFile = fileInfo
    this.hasData = data.nodes && data.nodes.length > 0
    this.statistics = data.statistics || {}
    
    // 保存到localStorage
    this.saveToStorage()
  },
  
  // 更新统计信息
  updateStatistics(stats) {
    this.statistics = stats
    this.saveToStorage()
  },
  
  // 清除数据
  clearData() {
    this.graphData = { nodes: [], links: [] }
    this.originalGraphData = { nodes: [], links: [] }
    this.statistics = {}
    this.currentFile = null
    this.hasData = false
    
    // 清除localStorage
    this.clearStorage()
  },
  
  // 更新过滤后的数据（用于搜索功能）
  updateFilteredData(filteredData) {
    if (filteredData) {
      this.graphData = {
        nodes: filteredData.nodes,
        links: filteredData.links
      }
    } else {
      // 清除搜索：恢复原始数据
      this.graphData = {
        nodes: [...this.originalGraphData.nodes],
        links: [...this.originalGraphData.links]
      }
    }
  },
  
                // 获取当前用户ID
              getCurrentUserId() {
                try {
                  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
                  return userInfo.userId || 'anonymous'
                } catch (error) {
                  return 'anonymous'
                }
              },

              // 获取存储键名
              getStorageKey() {
                const userId = this.getCurrentUserId()
                return `knowledgeGraphData_${userId}`
              },

              // 保存到sessionStorage
              saveToStorage() {
                try {
                  const dataToSave = {
                    currentFile: this.currentFile,
                    graphData: this.graphData,
                    originalGraphData: this.originalGraphData,
                    statistics: this.statistics,
                    hasData: this.hasData,
                    timestamp: Date.now()
                  }
                  const storageKey = this.getStorageKey()
                  sessionStorage.setItem(storageKey, JSON.stringify(dataToSave))
                } catch (error) {
                  console.error('保存知识图谱数据到sessionStorage失败:', error)
                }
              },

              // 从sessionStorage恢复数据
              loadFromStorage() {
                try {
                  const storageKey = this.getStorageKey()
                  const savedData = sessionStorage.getItem(storageKey)
                  if (savedData) {
                    const data = JSON.parse(savedData)

                    // 检查数据是否过期（24小时）
                    const now = Date.now()
                    const dataAge = now - (data.timestamp || 0)
                    const maxAge = 24 * 60 * 60 * 1000 // 24小时

                    if (dataAge < maxAge) {
                      this.currentFile = data.currentFile
                      this.graphData = data.graphData || { nodes: [], links: [] }
                      this.originalGraphData = data.originalGraphData || { nodes: [], links: [] }
                      this.statistics = data.statistics || {}
                      this.hasData = data.hasData || false
                      return true
                    } else {
                      // 数据过期，清除
                      this.clearStorage()
                    }
                  }
                } catch (error) {
                  console.error('从sessionStorage恢复知识图谱数据失败:', error)
                  this.clearStorage()
                }
                return false
              },

              // 清除sessionStorage
              clearStorage() {
                try {
                  const storageKey = this.getStorageKey()
                  sessionStorage.removeItem(storageKey)
                } catch (error) {
                  console.error('清除sessionStorage中的知识图谱数据失败:', error)
                }
              }
})

export default knowledgeGraphState
