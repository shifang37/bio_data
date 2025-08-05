<template>
  <div class="knowledge-graph-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="page-title">知识图谱</h1>
          <p class="page-description">上传并可视化JSON或CSV格式的知识图谱数据</p>
        </div>
        <div class="header-right">
          <el-button-group>
            <el-button 
              type="primary" 
              @click="showUploadDialog = true"
              :icon="Upload"
            >
              上传文件
            </el-button>
                         <el-dropdown @command="handleExport" trigger="click">
               <el-button :icon="Download">
                 导出图片<el-icon class="el-icon--right"><arrow-down /></el-icon>
               </el-button>
               <template #dropdown>
                 <el-dropdown-menu>
                   <el-dropdown-item command="png">导出为PNG</el-dropdown-item>
                   <el-dropdown-item command="jpg">导出为JPG</el-dropdown-item>
                 </el-dropdown-menu>
               </template>
             </el-dropdown>
          </el-button-group>
        </div>
      </div>
    </div>

    <!-- 主要内容 -->
    <div class="page-content">
      <!-- 文件信息栏 -->
      <div v-if="currentFile" class="file-info-bar">
        <div class="file-info">
          <el-icon><Document /></el-icon>
          <span class="file-name">{{ currentFile.name }}</span>
          <el-tag :type="currentFile.type === 'json' ? 'primary' : 'success'" size="small">
            {{ currentFile.type.toUpperCase() }}
          </el-tag>
          <span class="file-size">{{ formatFileSize(currentFile.size) }}</span>
        </div>
        <div class="file-actions">
          <el-button @click="reloadData" :icon="Refresh" size="small">重新加载</el-button>
          <el-button @click="clearData" :icon="Delete" size="small" type="danger">清除数据</el-button>
        </div>
      </div>

      <!-- 图谱查看器 -->
      <div class="graph-viewer-container">
        <KnowledgeGraphD3
          :graph-data="graphData"
          :statistics="statistics"
          @node-click="handleNodeClick"
          @filter-change="handleFilterChange"
        />
      </div>
    </div>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传知识图谱文件"
      width="600px"
      :close-on-click-modal="false"
    >
      <div class="upload-dialog-content">
        <!-- 文件类型选择 -->
        <div class="file-type-selector">
          <el-radio-group v-model="uploadFileType">
            <el-radio label="json">JSON格式</el-radio>
            <el-radio label="csv">CSV格式</el-radio>
          </el-radio-group>
        </div>

        <!-- 文件上传区域 -->
        <el-upload
          ref="uploadRef"
          class="upload-area"
          drag
          :auto-upload="false"
          :show-file-list="false"
          :accept="uploadFileType === 'json' ? '.json' : '.csv'"
          :on-change="handleFileChange"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将{{ uploadFileType.toUpperCase() }}文件拖到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 {{ uploadFileType === 'json' ? 'JSON' : 'CSV' }} 格式文件，大小不超过10MB
            </div>
          </template>
        </el-upload>

        <!-- 文件预览 -->
        <div v-if="uploadFile" class="file-preview">
          <div class="preview-header">
            <h4>文件预览</h4>
            <el-tag :type="uploadFileType === 'json' ? 'primary' : 'success'">
              {{ uploadFile.name }}
            </el-tag>
          </div>
          
          <!-- JSON预览 -->
          <div v-if="uploadFileType === 'json'" class="json-preview">
            <el-input
              v-model="jsonPreview"
              type="textarea"
              :rows="8"
              readonly
              placeholder="JSON内容将在这里显示..."
            />
          </div>
          
          <!-- CSV预览 -->
          <div v-if="uploadFileType === 'csv'" class="csv-preview">
            <el-table
              :data="csvPreview.slice(0, 5)"
              style="width: 100%"
              size="small"
              max-height="200"
            >
              <el-table-column
                v-for="column in csvColumns"
                :key="column"
                :prop="column"
                :label="column"
                show-overflow-tooltip
              />
            </el-table>
            <div v-if="csvPreview.length > 5" class="preview-more">
              还有 {{ csvPreview.length - 5 }} 行数据...
            </div>
          </div>
        </div>

        <!-- CSV解析选项 -->
        <div v-if="uploadFileType === 'csv'" class="csv-options">
          <h4>CSV解析选项</h4>
          <el-form :model="csvOptions" label-width="100px" size="small">
            <el-form-item label="分隔符">
              <el-select v-model="csvOptions.delimiter" style="width: 120px">
                <el-option label="逗号 (,)" value="," />
                <el-option label="分号 (;)" value=";" />
                <el-option label="制表符" value="\t" />
              </el-select>
            </el-form-item>
            <el-form-item label="编码格式">
              <el-select v-model="csvOptions.encoding" style="width: 120px">
                <el-option label="UTF-8" value="utf-8" />
                <el-option label="GBK" value="gbk" />
              </el-select>
            </el-form-item>
            <el-form-item label="首行为标题">
              <el-switch v-model="csvOptions.header" />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button 
            type="primary" 
            @click="processFile"
            :loading="processing"
            :disabled="!uploadFile"
          >
            {{ processing ? '处理中...' : '解析并加载' }}
          </el-button>
        </div>
      </template>
    </el-dialog>


  </div>
</template>

<script>
import { ref, reactive, computed, onMounted } from 'vue'
import { 
  Upload, Download, Document, Refresh, Delete, ArrowDown, UploadFilled 
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Papa from 'papaparse'
import api, { userState } from '../utils/api'
import KnowledgeGraphD3 from '../components/KnowledgeGraphD3.vue'

export default {
  name: 'KnowledgeGraph',
  components: {
    KnowledgeGraphD3,
    Upload, Download, Document, Refresh, Delete, ArrowDown, UploadFilled
  },
  setup() {
    // 响应式数据
    const showUploadDialog = ref(false)

    const uploadFileType = ref('json')
    const uploadFile = ref(null)
    const processing = ref(false)
    const currentFile = ref(null)
    const graphData = ref({ nodes: [], links: [] })
    const statistics = ref({})
    const selectedNodeDetails = ref(null)
    const jsonPreview = ref('')
    const csvPreview = ref([])
    const csvColumns = ref([])
    
    const csvOptions = reactive({
      delimiter: ',',
      encoding: 'utf-8',
      header: true
    })

    // 计算属性
    const hasData = computed(() => {
      return graphData.value.nodes && graphData.value.nodes.length > 0
    })

    // 方法
    const handleFileChange = (file) => {
      uploadFile.value = file.raw
      previewFile(file.raw)
    }

    const previewFile = async (file) => {
      try {
        if (uploadFileType.value === 'json') {
          const content = await readFileAsText(file)
          jsonPreview.value = content.substring(0, 1000) + (content.length > 1000 ? '...' : '')
        } else if (uploadFileType.value === 'csv') {
          const content = await readFileAsText(file)
          Papa.parse(content, {
            header: csvOptions.header,
            delimiter: csvOptions.delimiter,
            skipEmptyLines: true,
            complete: (results) => {
              csvPreview.value = results.data
              if (results.data.length > 0) {
                csvColumns.value = Object.keys(results.data[0])
              }
            },
            error: (error) => {
              console.error('CSV解析错误:', error)
              ElMessage.error('CSV文件预览失败')
            }
          })
        }
      } catch (error) {
        console.error('文件预览失败:', error)
        ElMessage.error('文件预览失败: ' + error.message)
      }
    }

    const readFileAsText = (file) => {
      return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = (e) => resolve(e.target.result)
        reader.onerror = (e) => reject(new Error('文件读取失败'))
        reader.readAsText(file, csvOptions.encoding)
      })
    }

    const processFile = async () => {
      if (!uploadFile.value) {
        ElMessage.warning('请先选择文件')
        return
      }

      processing.value = true

      try {
        const content = await readFileAsText(uploadFile.value)
        
        let requestData = {
          fileName: uploadFile.value.name,
          userId: userState.userId,
          userType: userState.userType
        }

        let response
        if (uploadFileType.value === 'json') {
          requestData.jsonContent = content
          response = await api.post('/api/knowledge-graph/parse/json', requestData)
        } else {
          // 解析CSV
          const csvData = await parseCSV(content)
          requestData.csvData = csvData
          response = await api.post('/api/knowledge-graph/parse/csv', requestData)
        }

        if (response.data.success) {
          graphData.value = response.data.data
          statistics.value = response.data.data.statistics || {}
          currentFile.value = {
            name: uploadFile.value.name,
            type: uploadFileType.value,
            size: uploadFile.value.size
          }
          
          showUploadDialog.value = false
          ElMessage.success('文件解析成功！')
          
          // 计算统计信息
          await calculateStatistics()
        } else {
          throw new Error(response.data.error || '文件解析失败')
        }
      } catch (error) {
        console.error('文件处理失败:', error)
        ElMessage.error('文件处理失败: ' + error.message)
      } finally {
        processing.value = false
      }
    }

    const parseCSV = (content) => {
      return new Promise((resolve, reject) => {
        Papa.parse(content, {
          header: csvOptions.header,
          delimiter: csvOptions.delimiter,
          skipEmptyLines: true,
          complete: (results) => {
            if (results.errors.length > 0) {
              reject(new Error('CSV解析错误: ' + results.errors[0].message))
            } else {
              resolve(results.data)
            }
          },
          error: (error) => {
            reject(error)
          }
        })
      })
    }

    const calculateStatistics = async () => {
      try {
        const response = await api.post('/api/knowledge-graph/statistics', {
          graphData: graphData.value,
          userId: userState.userId,
          userType: userState.userType
        })

        if (response.data.success) {
          statistics.value = response.data.statistics
        }
      } catch (error) {
        console.error('统计信息计算失败:', error)
      }
    }

    

         const handleExport = async (format) => {
       if (!hasData.value) {
         ElMessage.warning('没有可导出的数据')
         return
       }

       try {
         // 获取SVG元素
         const svgElement = document.querySelector('#knowledge-graph-svg')
         if (!svgElement) {
           ElMessage.error('未找到图谱元素，请确保图谱已加载')
           return
         }

         // 获取图谱容器的实际尺寸
         const graphContainer = document.querySelector('.graph-container')
         if (!graphContainer) {
           ElMessage.error('未找到图谱容器')
           return
         }

         const containerWidth = graphContainer.clientWidth
         const containerHeight = graphContainer.clientHeight

         // 创建Canvas，使用容器的实际尺寸
         const canvas = document.createElement('canvas')
         const ctx = canvas.getContext('2d')
         canvas.width = containerWidth
         canvas.height = containerHeight

         // 设置白色背景
         ctx.fillStyle = '#ffffff'
         ctx.fillRect(0, 0, containerWidth, containerHeight)

         // 获取SVG的完整内容，包括所有节点和边
         const svgClone = svgElement.cloneNode(true)
         
         // 移除缩放变换，确保看到完整的图谱
         const transformGroup = svgClone.querySelector('g')
         if (transformGroup) {
           transformGroup.removeAttribute('transform')
         }
         
         // 计算图谱的实际边界
         const allElements = svgClone.querySelectorAll('*')
         let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
         
         // 遍历所有元素找到边界
         allElements.forEach(element => {
           const bbox = element.getBBox ? element.getBBox() : null
           if (bbox && bbox.width > 0 && bbox.height > 0) {
             minX = Math.min(minX, bbox.x)
             minY = Math.min(minY, bbox.y)
             maxX = Math.max(maxX, bbox.x + bbox.width)
             maxY = Math.max(maxY, bbox.y + bbox.height)
           }
         })
         
         // 如果没有找到有效边界，使用容器尺寸
         if (minX === Infinity) {
           minX = 0
           minY = 0
           maxX = containerWidth
           maxY = containerHeight
         }
         
         // 添加边距
         const padding = 50
         minX -= padding
         minY -= padding
         maxX += padding
         maxY += padding
         
         const viewBoxWidth = maxX - minX
         const viewBoxHeight = maxY - minY
         
         // 确保SVG有正确的尺寸和viewBox
         svgClone.setAttribute('width', containerWidth)
         svgClone.setAttribute('height', containerHeight)
         svgClone.setAttribute('viewBox', `${minX} ${minY} ${viewBoxWidth} ${viewBoxHeight}`)
         
         // 将SVG转换为图片
         const svgData = new XMLSerializer().serializeToString(svgClone)
         const svgBlob = new Blob([svgData], { type: 'image/svg+xml;charset=utf-8' })
         const svgUrl = URL.createObjectURL(svgBlob)

         const img = new Image()
         img.onload = () => {
           // 绘制图片到Canvas，保持原始尺寸
           ctx.drawImage(img, 0, 0, containerWidth, containerHeight)
           
           // 导出图片
           const mimeType = format === 'png' ? 'image/png' : 'image/jpeg'
           const quality = format === 'jpg' ? 0.9 : undefined
           
           canvas.toBlob((blob) => {
             const url = URL.createObjectURL(blob)
             const link = document.createElement('a')
             link.href = url
             link.download = `knowledge-graph.${format}`
             document.body.appendChild(link)
             link.click()
             document.body.removeChild(link)
             URL.revokeObjectURL(url)
             URL.revokeObjectURL(svgUrl)
             
             ElMessage.success(`图谱已导出为${format.toUpperCase()}格式`)
           }, mimeType, quality)
         }
         
         img.onerror = () => {
           ElMessage.error('图片生成失败')
           URL.revokeObjectURL(svgUrl)
         }
         
         img.src = svgUrl
       } catch (error) {
         console.error('导出失败:', error)
         ElMessage.error('导出失败: ' + error.message)
       }
     }



    const reloadData = () => {
      if (currentFile.value) {
        ElMessage.info('数据已重新加载')
        calculateStatistics()
      }
    }

    const clearData = () => {
      ElMessageBox.confirm(
        '确定要清除当前的图谱数据吗？',
        '清除数据',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        graphData.value = { nodes: [], links: [] }
        statistics.value = {}
        currentFile.value = null
        selectedNodeDetails.value = null
        ElMessage.success('数据已清除')
      }).catch(() => {
        // 用户取消
      })
    }

    const handleNodeClick = (node) => {
      selectedNodeDetails.value = node
      // 不显示弹窗，右侧详情面板会自动显示
    }

    const handleFilterChange = async (filters) => {
      try {
        const response = await api.post('/api/knowledge-graph/filter', {
          graphData: graphData.value,
          filters: filters,
          userId: userState.userId,
          userType: userState.userType
        })

        if (response.data.success) {
          // 这里可以更新图谱显示，或者保持原数据不变，只在前端过滤
          ElMessage.success('过滤条件已应用')
        }
      } catch (error) {
        console.error('过滤失败:', error)
        ElMessage.error('过滤失败: ' + error.message)
      }
    }

    const showNeighbors = async () => {
      if (!selectedNodeDetails.value) return

      try {
        const response = await api.post('/api/knowledge-graph/neighbors', {
          graphData: graphData.value,
          nodeId: selectedNodeDetails.value.id,
          depth: 1,
          userId: userState.userId,
          userType: userState.userType
        })

        if (response.data.success) {
          const neighbors = response.data.data
          ElMessage.success(`找到 ${neighbors.neighborCount} 个邻居节点`)
          // 这里可以高亮显示邻居节点
        }
      } catch (error) {
        console.error('获取邻居节点失败:', error)
        ElMessage.error('获取邻居节点失败: ' + error.message)
      }
    }

    const formatFileSize = (bytes) => {
      if (bytes === 0) return '0 Bytes'
      const k = 1024
      const sizes = ['Bytes', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }

    // 生命周期
    onMounted(() => {
      // 页面加载时可以检查是否有缓存数据
    })

    return {
      // refs
      showUploadDialog,

      uploadFileType,
      uploadFile,
      processing,
      currentFile,
      graphData,
      statistics,
      selectedNodeDetails,
      jsonPreview,
      csvPreview,
      csvColumns,
      csvOptions,

      // computed
      hasData,

      // methods
      handleFileChange,
      processFile,
      handleExport,
      reloadData,
      clearData,
      handleNodeClick,
      handleFilterChange,
      showNeighbors,
      formatFileSize
    }
  }
}
</script>

<style scoped>
.knowledge-graph-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.page-header {
  background: white;
  border-bottom: 1px solid #e9ecef;
  padding: 20px 24px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left h1 {
  margin: 0 0 4px 0;
  font-size: 24px;
  color: #333;
}

.header-left p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.page-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.file-info-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-name {
  font-weight: 500;
  color: #333;
}

.file-size {
  color: #666;
  font-size: 13px;
}

.file-actions {
  display: flex;
  gap: 8px;
}

.graph-viewer-container {
  flex: 1;
  overflow: hidden;
}

.upload-dialog-content {
  padding: 0;
}

.file-type-selector {
  margin-bottom: 20px;
  text-align: center;
}

.upload-area {
  margin-bottom: 20px;
}

.file-preview {
  margin-bottom: 20px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.preview-header h4 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.json-preview,
.csv-preview {
  border: 1px solid #e9ecef;
  border-radius: 4px;
  overflow: hidden;
}

.preview-more {
  padding: 8px 12px;
  background: #f8f9fa;
  color: #666;
  font-size: 13px;
  text-align: center;
}

.csv-options {
  padding-top: 20px;
  border-top: 1px solid #e9ecef;
}

.csv-options h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  color: #333;
}

.node-dialog-content {
  padding: 0;
}

.node-properties {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e9ecef;
}

.node-properties h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  color: #333;
}

.dialog-footer {
  text-align: right;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    gap: 16px;
  }

  .file-info-bar {
    flex-direction: column;
    gap: 12px;
  }

  .file-info {
    width: 100%;
    justify-content: center;
  }

  .file-actions {
    width: 100%;
    justify-content: center;
  }
}
</style>