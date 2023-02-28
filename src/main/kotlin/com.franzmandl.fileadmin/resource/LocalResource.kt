package com.franzmandl.fileadmin.resource

import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.HttpException
import com.franzmandl.fileadmin.model.ApplicationCtx
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
import java.time.format.DateTimeFormatter

@Controller
class LocalResource(
    @Autowired private val applicationCtx: ApplicationCtx
) : BaseResource() {
    @RequestMapping(value = [ApplicationCtx.RequestMappingPaths.Local.task], method = [RequestMethod.GET])
    @ResponseBody
    fun getTask(
        @RequestParam(name = "start") startString: String,
        @RequestParam(name = "now") nowString: String,
    ): ResponseEntity<String> {
        val start = LocalDate.parse(startString, DateTimeFormatter.ISO_LOCAL_DATE)
        val now = LocalDate.parse(nowString, DateTimeFormatter.ISO_LOCAL_DATE)
        val requestCtx = applicationCtx.createRequestCtx()
        val stringBuilder = StringBuilder()
        val taskMailDirectory = requestCtx.getInode(applicationCtx.paths.taskMail ?: throw HttpException.badRequest("taskMail is null."))
        for (projectDirectory in CommonUtil.getSequenceOfChildren(requestCtx, taskMailDirectory)) {
            if (!projectDirectory.contentOperation.canDirectoryGet) {
                continue
            }
            for (statusDirectory in CommonUtil.getSequenceOfChildren(requestCtx, projectDirectory)) {
                if (!statusDirectory.contentOperation.canDirectoryGet) {
                    continue
                }
                val tasks = CommonUtil.getSequenceOfChildren(requestCtx, statusDirectory).toList()
                val taskCtx = TaskCtx(requestCtx, sortedSetOf(), tasks)
                for (task in tasks) {
                    try {
                        val taskDate = taskCtx.registry.getOrCreateTaskDate(task)
                        val taskLastModified = taskDate.getLastModified()
                        if (start <= taskDate.date && taskDate.date <= now && (taskLastModified != now || taskLastModified == null)) {
                            stringBuilder.appendLine("task/${projectDirectory.path.name}/${statusDirectory.path.name}/${task.path.name}").appendLine()
                        }
                    } catch (e: TaskException) {
                        stringBuilder.appendLine(task.toString()).appendLine(e).appendLine()
                    }
                }
            }
        }
        return ResponseEntity(stringBuilder.toString(), CommonUtil.createContentTypePlainUtf8HttpHeaders(), HttpStatus.OK)
    }
}