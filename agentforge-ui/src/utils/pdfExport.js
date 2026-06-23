import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'

/**
 * 将DOM元素导出为PDF
 * @param {HTMLElement} element - 要导出的DOM元素
 * @param {string} title - PDF文件名
 */
export async function exportToPdf(element, title = '报表') {
  if (!element) return

  // 临时样式：确保截图完整
  element.style.overflow = 'visible'

  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#f5f5f5',
    logging: false
  })

  element.style.overflow = ''

  const imgWidth = 210 // A4 宽度 mm
  const imgHeight = (canvas.height * imgWidth) / canvas.width
  const pageHeight = 297 // A4 高度 mm

  const pdf = new jsPDF('p', 'mm', 'a4')
  let position = 0

  // 分页处理：内容超过一页时自动分页
  while (position < imgHeight) {
    pdf.addImage(
      canvas.toDataURL('image/jpeg', 0.95),
      'JPEG',
      0,
      -position,
      imgWidth,
      imgHeight
    )
    position += pageHeight
    if (position < imgHeight) {
      pdf.addPage()
    }
  }

  pdf.save(`${title}.pdf`)
}
