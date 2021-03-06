package db

import java.sql.{Timestamp, DriverManager, ResultSet, SQLException}
import scala.collection.JavaConversions._

import domain._
import scala.collection.mutable.ListBuffer
import java.util.Date
import java.util

class BookNotFound extends Exception {
}


class DBHandler(dbFile: String) {
  val driver = "org.sqlite.JDBC"
  val statBld = new StatementBuilder()
  val authorStmt = new AuthorStatementBuilder()
  val bookStmt = new BookStatementBuilder()
  val categoryStmt = new CategoryStatementBuilder()
  val tagStmt = new TagStatementBuilder()
  val prioritizedBookStmt = new PrioritizedBookStatementBuilder()


  def this() = {
    this("sample.db")
  }

  def prepareConnection() = {
    Class.forName(this.driver)
    DriverManager.getConnection("jdbc:sqlite:" + dbFile)
  }

  def removeTag(tag: Tag) {
    val connection = prepareConnection()
    val stat = tagStmt.removeTagStatement(connection)
    stat.setInt(1, tag.id)
    stat.executeUpdate()
    connection.close()
  }

  def findTag(tagText: String): Tag = {
    val connection = prepareConnection()
    val stat = tagStmt.findTagStatement(connection)
    stat.setString(1, tagText)
    val rs = stat.executeQuery()
    val tag = new Tag(tagText)
    if (rs.next()){
      tag.id =rs.getInt(1)
    }
    else {
      tag.id = -1
    }
    connection.close()
    tag
  }

  def findAllTagsByBookId(bookId: Int): List[Tag] = {
    val connection = prepareConnection()
    val stat = tagStmt.findTagsByBookIdStatement(connection)
    stat.setInt(1, bookId)
    val rs = stat.executeQuery()
    val tags = ListBuffer[Tag]()
    while (rs.next()){
      val tag = new Tag(rs.getString("tag"))
      tag.id =rs.getInt(1)
      tags += tag
    }

    connection.close()
    tags.toList
  }


  def addTag(tag: Tag) {
    val tagFromDB = findTag(tag.tag)
    if(tagFromDB.id != -1) {
      tag.id = tagFromDB.id
    }
    else {
      val connection = prepareConnection()
      val stat = tagStmt.addTagStatement(connection)
      stat.setString(1, tag.tag)
      stat.executeUpdate()

      val rs = stat.getGeneratedKeys()
      if (rs.next()){
        tag.id =rs.getInt(1)
      }
      connection.close()
    }

  }

  def getAllTags() = {
    val connection = prepareConnection()
    val stat = tagStmt.getAllTagsStatement(connection)
    val allTags = ListBuffer[Tag]()
    val rs = stat.executeQuery()
    while (rs.next()){
      val id = rs.getInt("id")
      val tagText = rs.getString("tag")
      allTags += new Tag(id, tagText)
    }
    connection.close()
    allTags.toList
  }

  def findCategoryID(category: Category) = {
    val connection = prepareConnection()
    val stat = categoryStmt.findCategoryStatement(connection)
    stat.setString(1, category.category)
    val rs: ResultSet = stat.executeQuery()
    var id = -1
    if (rs.next()) {
      id = rs.getInt("id")
    }
    connection.close()
    id
  }

  def addCategory(category: Category) {
    val id = findCategoryID(category)
    if(id == -1) {
      val connection = prepareConnection()
      val stat = categoryStmt.addCategoryStatement(connection)
      stat.setString(1, category.category)
      stat.executeUpdate()

      val rs = stat.getGeneratedKeys()
      if (rs.next()){
        category.id =rs.getInt(1)
      }
      connection.close()
    }
    else {
      category.id = id
    }

  }

  def addBookAuthorRelation(book: Book, author: Author)  {
    val connection = prepareConnection()
    val stat = bookStmt.addBookAuthorRelation(connection)
    stat.setInt(1, book.id)
    stat.setInt(2, author.id)
    stat.executeUpdate()
    connection.close()
  }

  def addBookTagRelation(book: Book, tag: Tag)  {
    val connection = prepareConnection()
    val stat = bookStmt.addBookTagRelation(connection)
    stat.setInt(1, book.id)
    stat.setInt(2, tag.id)
    stat.executeUpdate()
    connection.close()
  }

  def addBooks(books: List[Book]) {
    for(book <- books)
      addBook(book)
  }

  def addBook(book: Book) {
    addBook(book, List(book.getAuthor))
  }

  def addBook(book: Book, authors: List[Author]) {
    val bookIDFromDB = findBookByTitleAndAuthor(book.getTitle(), authors.head.getName)
    if (bookIDFromDB != -1) {
      removeBook(bookIDFromDB)
    }

    val connection = prepareConnection()
    for (author <- authors) {
      if (author.id == -1) {
        addAuthor(author)
      }
    }
    if (book.category.id == -1) {
      addCategory(book.category)
    }
    for (tag: Tag <- book.tags.toList) {
      if (tag.id == -1)  {
        addTag(tag)
      }

    }
    val stat = bookStmt.addBookStatement(connection)
    stat.setString(1, book.getTitle)
    stat.setString(2, book.getPathToContent)
    stat.setString(3, book.description)
    stat.setInt(4, book.category.id)
    stat.executeUpdate()

    val rs = stat.getGeneratedKeys()
    if (rs.next()){
      book.id =rs.getInt(1)
    }

    for(author <- authors) {
      addBookAuthorRelation(book, author)
    }

    for(tag: Tag <- book.tags) {
      addBookTagRelation(book, tag)
    }
    connection.close()


  }

  def removeBook(book: Book) {
    val connection = prepareConnection()
    val stat = bookStmt.removeBookStatement(connection)
    stat.setInt(1, book.id)
    stat.executeUpdate()
    val author = book.getAuthor
    removeBookAuthorRelation(book, author)
    connection.close()
  }

  def removeBook(id: Int) {
    val connection = prepareConnection()
    val book = findBookById(id)

    val author = book.getAuthor
    removeBookAuthorRelation(book, author)
    for(tag <- book.tags) {
      removeBookTagRelation(book, tag)
    }
    val stat = bookStmt.removeBookStatement(connection)
    stat.setInt(1, id)
    stat.executeUpdate()
    connection.close()
  }

  def isAuthorWithoutBooks(author: Author) = {
    false
  }

  def removeBookAuthorRelation(book: Book, author: Author) {
    val connection = prepareConnection()
    val stat = bookStmt.removeBookAuthorRelationStatement(connection)
    stat.setInt(1, book.id)
    stat.setInt(2, author.id)
    stat.executeUpdate()
    if (isAuthorWithoutBooks(author))
      removeAuthor(author)
    connection.close()
  }

  def isTagWithoutBooks(tag: Tag) = {
    false
  }

  def removeBookTagRelation(book: Book, tag: Tag) {
    val connection = prepareConnection()
    val stat = bookStmt.removeBookTagRelationStatement(connection)
    stat.setInt(1, book.id)
    stat.setInt(2, tag.id)
    stat.executeUpdate()
    if (isTagWithoutBooks(tag))
      removeTag(tag)
    connection.close()
  }

  def removeAuthor(author: Author) {
    val connection = prepareConnection()
    val books = findBooksByAuthor(author)
    val stat = authorStmt.removeAuthorStatement(connection)
    stat.setString(1, author.getName)
    stat.executeUpdate()
    for (book <- books) {
      removeBook(book)
    }
    connection.close()
  }

  def removePrioritizedBook(book: PrioritizedBook) {
    val connection = prepareConnection()
    val stat = prioritizedBookStmt.removePrioritizedBook(connection)
    stat.setInt(1, book.book.id)
    stat.executeUpdate()
    connection.close()
  }

  def removePrioritizedBook(id: Int) {
    val connection = prepareConnection()
    val stat = prioritizedBookStmt.removePrioritizedBook(connection)
    stat.setInt(1, id)
    stat.executeUpdate()
    connection.close()
  }

  def getCategoryByID(categoryID: Int) = {

    val connection = prepareConnection()
    val stat = categoryStmt.findCategoryByIDStatement(connection)
    stat.setInt(1, categoryID)
    val rs = stat.executeQuery()
    var category = "Not found"
    if(rs.next()) {
      category = rs.getString("category")
      rs.close()
      connection.close()
    }
    val result = new Category(category)
    result.id = categoryID
    result
  }

  def makeBookFromResultSet(rs: ResultSet): Book = {
    val bookId = rs.getInt("id")
    val path = rs.getString("path_to_content")
    val title = rs.getString("title")
    val category_id = rs.getInt("category_id")
    val category = getCategoryByID(category_id)
    val description = rs.getString("description")
    val author_name = rs.getString("name")
    val info_about_author = rs.getString("additional_info")
    val tags = findAllTagsByBookId(bookId)

    val author = new Author(author_name, info_about_author)
    val book = new Book(title, author, path, description, category)
    book.id = bookId
    book.tags = new util.ArrayList(tags)
    book

  }

  def makeAuthorFromResultSet(rs: ResultSet): Author = {
    val name = rs.getString("name")
    val additionalInfo = rs.getString("additional_info")
    new Author(name, additionalInfo)
  }

  def findBookById(id: Int): Book = {
    val connection = prepareConnection()
    val stat = bookStmt.findBookByIdStatement(connection)
    stat.setInt(1, id)
    val rs = stat.executeQuery()
    if(rs.next()) {
      val book = makeBookFromResultSet(rs)
      book.id = id
      rs.close()
      connection.close()
      book
    }
    else
      throw new BookNotFound()
  }

  def findBook(title: String): Book = {
    val connection = prepareConnection()
    val stat = bookStmt.findBookStatement(connection)
    stat.setString(1, title)
    val rs = stat.executeQuery()
    if(rs.next()) {
      val book = makeBookFromResultSet(rs)
      book.id = rs.getInt("id")
      rs.close()
      connection.close()
      book
    }
    else
      throw new BookNotFound()
  }

  def findBooksByAuthor(author: Author) = {
    val connection = prepareConnection()
    val stat = bookStmt.findBookByAuthorStatement(connection)
    stat.setInt(1, author.id)
    val rs = stat.executeQuery()
    var books = List[Book]()
    while (rs.next()) {
      val path = rs.getString("path_to_content")
      val title = rs.getString("title")
      books = books :+ (new Book(title, author, path))
    }
    rs.close()
    connection.close()
    books
  }

  def findBookByTitleAndAuthor(title: String, authorName: String) = {
    val connection = prepareConnection()
    val stat = bookStmt.findBookIDByTitleAndAuthorStatement(connection)
    stat.setString(1, title)
    stat.setString(2, authorName)
    val rs = stat.executeQuery()
    var id = -1
    while (rs.next()) {
      id = rs.getInt("id")
    }
    rs.close()
    connection.close()
    id
  }

  def findBooksByCategory(category: String) = {
    val connection = prepareConnection()
    val stat = bookStmt.findBookByCategoryStatement(connection)
    stat.setString(1, category)
    val rs = stat.executeQuery()
    var books = List[Book]()
    while (rs.next()) {
      books = books :+ makeBookFromResultSet(rs)
    }
    rs.close()
    connection.close()
    books
  }

  def getAllBooks() = {
    val connection = prepareConnection()
    val stat = bookStmt.getAllBooksStatement(connection)
    val rs = stat.executeQuery()
    var books = List[Book]()
    while (rs.next()) {
      books = books :+ (makeBookFromResultSet(rs))
    }
    rs.close()
    connection.close()
    books
  }

  import scala.collection.JavaConversions._

  def getAllAuthors() = {
    val connection = prepareConnection()
    val stat = authorStmt.getAllAuthorsStatement(connection)
    val rs = stat.executeQuery()
    var authors = List[Author]()
    while (rs.next()) {
      authors = authors :+ makeAuthorFromResultSet(rs)
    }
    rs.close()
    connection.close()
    authors
  }

  def savePrioritizedBooks(booksToSave: List[PrioritizedBook]) {
    for(book <- booksToSave) {
      addPrioritizedBook(book)
    }
  }


  def addPrioritizedBook(prioritizedBook: PrioritizedBook) {
    val connection = prepareConnection()
    val stat = prioritizedBookStmt.addPrioritizedBook(connection)

    var bookID: Int = -1
    if(prioritizedBook.book.id != -1)
    {
      bookID = findBook(prioritizedBook.book.getTitle).id
    }
    else {
      bookID = prioritizedBook.book.id
    }
    stat.setInt(1, bookID)
    stat.setInt(2, prioritizedBook.priority)
    stat.setTimestamp(3, new Timestamp(prioritizedBook.deadline.getTime()))
    stat.executeUpdate()
    connection.close()
  }


  def getPrioritizedBooks() = {
    val connection = prepareConnection()
    val stat = prioritizedBookStmt.getAllPrioritizedBooks(connection)
    val rs = stat.executeQuery()
    var prioritizedBooks = ListBuffer[PrioritizedBook]()
    while (rs.next()) {

      try {
        val title = rs.getString("title")
        val book = findBook(title)
        val priority = rs.getInt("priority")
        val deadlineTimestamp = rs.getTimestamp("deadline")
        val deadline = new Date(deadlineTimestamp.getTime())
        val priorityBook = new PrioritizedBook(book, priority, deadline)
        prioritizedBooks += priorityBook
      }
      catch {
        case e: BookNotFound => {
          val prioritizedBookId = rs.getInt("id")
          removePrioritizedBook(prioritizedBookId)
        }
      }
    }
    rs.close()
    connection.close()
    prioritizedBooks.toList
  }

  def addAuthor(author: Author) {
    val connection = prepareConnection()
    val authorFromDB = findAuthor(author.getName)
    if (authorFromDB.getName == "Unknown") {
      val stat = authorStmt.addAuthorStatement(connection)
      stat.setString(1, author.getName)
      stat.setString(2, author.getAdditionalInfo)
      stat.executeUpdate()

      val rs = stat.getGeneratedKeys()
      if (rs.next()){
        author.id = rs.getInt(1)
      }

    }
    else
      author.id = authorFromDB.id
    connection.close()
  }

  def findAuthor(name: String) = {
    try {
      val connection = prepareConnection()
      val stat = authorStmt.findAuthorByNameStatement(connection)
      stat.setString(1, name)
      stat.executeQuery()
      val rs = stat.executeQuery()
      val author = if (rs.next()) {
        val additional_info = rs.getString("additional_info")
        val id = rs.getInt("id")
        val result =  new Author(name, additional_info)
        result.id = id
        result
      }
      else {
        new Author("Unknown")
      }
      rs.close()
      connection.close()
      author
    }
    catch {
      case ex: SQLException => {
        println("SQLException: " + ex.getSQLState)
        new Author("Unknown")
      }
    }
  }

  def createTablesInDB() {
    val connection = prepareConnection()
    val stat = connection.createStatement()

    stat.executeUpdate(
      """
        create table if not exists books
        (
          id integer primary key autoincrement,
          title string,
          path_to_content string,
          description string,
          category_id integer
        );

        create table if not exists authors
        (
          id integer primary key autoincrement,
          name string unique,
          additional_info string
        );

        create table if not exists book_authors
        (
          id integer primary key autoincrement,
          book_id integer,
          author_id integer
        );

        create table if not exists categories
        (
          id integer primary key autoincrement,
          category string unique
        );

        create table if not exists tags
        (
          id integer primary key autoincrement,
          tag string unique
        );

        create table if not exists book_tags
        (
          id integer primary key autoincrement,
          book_id integer,
          tag_id integer
        );

        create table if not exists priority_books
        (
          id integer primary key autoincrement,
          book_id integer,
          priority integer,
          deadline timestamp
        );
      """.stripMargin)
    connection.close()
  }

  def dropAllTables() {
    val connection = prepareConnection()
    val stat = connection.createStatement()
    stat.executeUpdate("drop table books")
    stat.executeUpdate("drop table authors")
    stat.executeUpdate("drop table book_authors")
    stat.executeUpdate("drop table categories")
    stat.executeUpdate("drop table tags")
    stat.executeUpdate("drop table book_tags")
  }
}
