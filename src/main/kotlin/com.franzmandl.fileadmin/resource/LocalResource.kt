package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.common.VersionInfo
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.task.TaskCtx
import com.franzmandl.fileadmin.task.TaskException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.time.LocalDate
import java.time.LocalDateTime

@Controller
class LocalResource(
    @Autowired private val applicationCtx: ApplicationCtx
) : BaseResource() {
    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Local.ping], method = [RequestMethod.GET])
    @ResponseBody
    fun getPing(): ResponseEntity<String> =
        ResponseEntity("${VersionInfo.banner}\n${LocalDateTime.now()}\n", CommonUtil.createContentTypePlainUtf8HttpHeaders(), HttpStatus.OK)

    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Local.task], method = [RequestMethod.GET])
    @ResponseBody
    fun getTask(
        @RequestParam @IsoDateFormat start: LocalDate,
        @RequestParam @IsoDateFormat now: LocalDate,
    ): ResponseEntity<String> {
        val mailPath = applicationCtx.task.mailPath ?: throw HttpException.badRequest("taskMail is null.")
        val requestCtx = applicationCtx.createRequestCtx(now)
        val stringBuilder = StringBuilder()
        for (projectDirectory in CommonUtil.getSequenceOfChildren(requestCtx, requestCtx.getInode(mailPath))) {
            if (!projectDirectory.inode0.contentOperation.canDirectoryGet) {
                continue
            }
            for (statusDirectory in CommonUtil.getSequenceOfChildren(requestCtx, projectDirectory)) {
                if (!statusDirectory.inode0.contentOperation.canDirectoryGet) {
                    continue
                }
                val tasks = CommonUtil.getSequenceOfChildren(requestCtx, statusDirectory).toList()
                val taskCtx = TaskCtx(requestCtx, sortedSetOf(), tasks)
                for (task in tasks) {
                    try {
                        val taskDate = taskCtx.registry.getOrCreateTaskDate(task)
                        val taskLastModified = taskDate.getLastModified()
                        if (taskDate.date in start..now && taskLastModified != now) {
                            stringBuilder.appendLine("task/${projectDirectory.inode0.path.name}/${statusDirectory.inode0.path.name}/${task.inode0.path.name}").appendLine()
                        }
                    } catch (e: TaskException) {
                        stringBuilder.appendLine(task.inode0.path.absoluteString).appendLine(e).appendLine()
                    }
                }
            }
        }
        return ResponseEntity(stringBuilder.toString(), CommonUtil.createContentTypePlainUtf8HttpHeaders(), HttpStatus.OK)
    }
}