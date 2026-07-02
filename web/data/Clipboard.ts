import { enqueueSnackbar } from "notistack";

export default async function CP(text: string) {
    try {
        await navigator.clipboard.writeText(text);
        enqueueSnackbar('文本已复制到剪切板', { variant: 'success' });
    } catch (err) {
        enqueueSnackbar(`复制失败: ${err}，尝试备用方案`, { variant: "error" });
        copyToClipboard(text)
    }
}

function copyToClipboard(text: string) {
    const textArea = document.createElement('textarea');
    textArea.style.position = 'fixed';
    textArea.style.visibility = 'hidden';
    textArea.value = text;
    document.body.appendChild(textArea);
    textArea.select();
    document.execCommand('copy');
    document.body.removeChild(textArea);
    enqueueSnackbar(`已尝试复制`, { variant: "info" });
};